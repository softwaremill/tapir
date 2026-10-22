package sttp.tapir.json.pickler.internal.compiletime

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.MacroCommonsScala3
import hearth.fp.DirectStyle.RunSafe
import hearth.fp.data.NonEmptyVector
import hearth.fp.effect.*
import hearth.std.*
import sttp.tapir.Schema
import sttp.tapir.internal.SNameMacros
import sttp.tapir.json.pickler.{CreateDerivedEnumerationPickler, Pickler, PicklerConfiguration}
import sttp.tapir.json.pickler.internal.runtime.PicklerFactories

import scala.quoted.*

/** The macro bundle behind [[Pickler]] derivation.
  *
  * The schema half ([[SchemaDerivation]]) walks the type graph and hoists one `lazy val` per schema into a shared `ValDefsCache`. The codec
  * half ([[CodecDerivation]]) walks the same graph and emits one `JsonCodecMaker.make` per case class / sealed hierarchy, configured from
  * the *same* [[DerivationEnv]]. A single `toValDefs.use` wraps the whole instance expression, so every hoisted definition sits at instance
  * scope and is built once. All four entry points go through [[runDerivation]], which owns that plumbing.
  */
final private[pickler] class PicklerMacros(q: Quotes)
    extends MacroCommonsScala3(using q)
    with DerivationTimeout
    with TypeShape
    with AnnotationSupport
    with NameSupport
    with ImplicitPicklerSupport
    with SchemaDerivation
    with CodecDerivation
    with PlatformSupportScala3 {

  override protected def derivationSettingsNamespace: String = "tapirPickler"

  /** Centralised `Type.of[...]` instances.
    *
    * These must not be written as `implicit val`s in the scope where they are also the implicit being summoned: Hearth resolves `Type[A]`
    * implicits lazily via cross-quotes, so a self-referential definition causes a stack overflow at macro-expansion time with no usable
    * stack trace. Keeping every `Type.of` behind a method/lazy val on this object, and assigning it to a local `implicit val` at each use
    * site, avoids that.
    */
  private[compiletime] object PTypes {
    def SchemaOf[A: Type]: Type[Schema[A]] = Type.of[Schema[A]]
    def PicklerOf[A: Type]: Type[Pickler[A]] = Type.of[Pickler[A]]
    def CodecOf[A: Type]: Type[JsonValueCodec[A]] = Type.of[JsonValueCodec[A]]
    def EnumBuilderOf[A: Type]: Type[CreateDerivedEnumerationPickler[A]] = Type.of[CreateDerivedEnumerationPickler[A]]
    def ListOf[A: Type]: Type[List[A]] = Type.of[List[A]]
    lazy val Config: Type[PicklerConfiguration] = Type.of[PicklerConfiguration]
    lazy val StringT: Type[String] = Type.of[String]
    lazy val LogDerivation: Type[Pickler.LogDerivation] = Type.of[Pickler.LogDerivation]
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Entry points
  // ---------------------------------------------------------------------------------------------------------------

  /** Full type class instance: `Schema` + `JsonValueCodec`, derived together. */
  def derivePickler[A: Type](configExpr: Expr[PicklerConfiguration]): Expr[Pickler[A]] = {
    implicit val SchemaA: Type[Schema[A]] = PTypes.SchemaOf[A]
    implicit val PicklerA: Type[Pickler[A]] = PTypes.PicklerOf[A]
    implicit val CodecA: Type[JsonValueCodec[A]] = PTypes.CodecOf[A]

    runDerivation[A, Pickler[A]]("Pickler.derived", "Pickler") { (cache, runSafe) =>
      val (schemaExpr, codecExpr) = runSafe {
        for {
          env <- environment(configExpr, root = typeKey[A])
          schema <- deriveSchemaRecursively[A](cache, env)
          codec <- deriveCodec[A](env)
        } yield (schema, codec)
      }
      Expr.quote(PicklerFactories.instance[A](Expr.splice(schemaExpr), Expr.splice(codecExpr)))
    }
  }

  /** Schema-only entry point. Like the others it needs the configuration at compile time: the names it splices are computed from it. */
  def deriveSchemaOnly[A: Type](configExpr: Expr[PicklerConfiguration]): Expr[Schema[A]] = {
    implicit val SchemaA: Type[Schema[A]] = PTypes.SchemaOf[A]
    runDerivation[A, Schema[A]]("Pickler.schemaFor", "Schema") { (cache, runSafe) =>
      runSafe {
        for {
          env <- environment(configExpr, root = typeKey[A])
          schema <- deriveSchemaRecursively[A](cache, env)
        } yield schema
      }
    }
  }

  /** `Pickler.derivedEnumeration[A]`: the builder behind `defaultStringBased` / `customStringBased`.
    *
    * The default schema and codec are exactly what `derivePickler` produces for `A` (an all-singleton hierarchy is a string enumeration on
    * both sides); the builder additionally receives the singleton values, from which `customStringBased` builds a runtime string codec and
    * the matching enumeration validator. Nothing about the user's `encode` function is needed at compile time.
    */
  def deriveEnumerationBuilder[A: Type](configExpr: Expr[PicklerConfiguration]): Expr[CreateDerivedEnumerationPickler[A]] = {
    implicit val SchemaA: Type[Schema[A]] = PTypes.SchemaOf[A]
    implicit val CodecA: Type[JsonValueCodec[A]] = PTypes.CodecOf[A]
    implicit val BuilderA: Type[CreateDerivedEnumerationPickler[A]] = PTypes.EnumBuilderOf[A]
    implicit val ListA: Type[List[A]] = PTypes.ListOf[A]
    val macroName = "Pickler.derivedEnumeration"

    runDerivation[A, CreateDerivedEnumerationPickler[A]](macroName, "enumeration Pickler") { (cache, runSafe) =>
      val (valuesExpr, schemaExpr, codecExpr) = runSafe {
        for {
          children <- enumerationCases[A](macroName)
          env <- environment(configExpr, root = typeKey[A])
          schema <- deriveSchemaRecursively[A](cache, env)
          codec <- deriveCodec[A](env)
        } yield (singletonValuesExpr[A](children), schema, codec)
      }
      Expr.quote(PicklerFactories.enumerationBuilder[A](Expr.splice(valuesExpr), Expr.splice(schemaExpr), Expr.splice(codecExpr)))
    }
  }

  /** The leaves of `A`, provided `A` is a sealed hierarchy whose leaves are all singletons. */
  private def enumerationCases[A: Type](macroName: String): MIO[List[(String, ??<:[A])]] =
    classify[A] match {
      case Shape.Enumeration(_, leaves)                 => MIO.pure(leaves)
      case Shape.Coproduct(_, leaves) if leaves.isEmpty => fail(PicklerDerivationError.noChildrenInSealedTrait(Type[A].plainPrint))
      case Shape.Coproduct(_, leaves)                   =>
        fail(PicklerDerivationError.notAnEnumeration(Type[A].plainPrint, nonSingletonLeaves(leaves)))
      case _ => fail(PicklerDerivationError.notASealedHierarchy(Type[A].plainPrint, macroName))
    }

  /** `Pickler.oneOfUsingField[A, V](extractor, asString)(v1 -> pickler1, ...)`.
    *
    * The discriminator value of each leaf is `asString(v)`, decided by the user rather than by the configuration; the discriminator *field*
    * is the configured one (`$type` by default). Not core's `Schema.oneOfUsingField`: that documents a discriminator field named after the
    * extractor (`code` for `_.code`), which the JSON does not contain.
    *
    * Apart from those values, this *is* `Pickler.derived[A]`: both halves are derived structurally, with `DerivationEnv.leafNameOverrides`
    * set to `asString(v)` per leaf. The child picklers in the mapping only tell us which leaf each value selects; neither their schemas nor
    * their codecs are used. Taking them would let the two halves drift (a child pickler derived under another configuration would document
    * field names the codec does not write), and a leaf codec derived on its own writes its configuration-derived tag, not the overridden
    * one. jsoniter needs the values as literals, which is why the keys and `asString` are evaluated at expansion time.
    *
    * The mapping must cover every leaf exactly once.
    *
    * `extractor` is accepted for API compatibility with core's `Schema.oneOfUsingField`; nothing is derived from it, since the JSON does
    * not carry that field.
    */
  def deriveOneOfUsingField[A: Type, V: Type](
      extractor: Expr[A => V],
      asString: Expr[V => String],
      mapping: VarArgs[(V, Pickler[? <: A])],
      configExpr: Expr[PicklerConfiguration]
  ): Expr[Pickler[A]] = {
    implicit val SchemaA: Type[Schema[A]] = PTypes.SchemaOf[A]
    implicit val PicklerA: Type[Pickler[A]] = PTypes.PicklerOf[A]
    implicit val CodecA: Type[JsonValueCodec[A]] = PTypes.CodecOf[A]
    implicit val StringT: Type[String] = PTypes.StringT
    val macroName = "Pickler.oneOfUsingField"
    val _ = extractor

    runDerivation[A, Pickler[A]](macroName, "Pickler (oneOfUsingField)") { (cache, runSafe) =>
      val (schemaExpr, codecExpr) = runSafe {
        for {
          leaves <- classify[A] match {
            case Shape.Coproduct(_, leaves) => MIO.pure(leaves)
            // An all-singleton hierarchy is a bare string: there is no object to put a discriminator in.
            case Shape.Enumeration(_, _) => fail(PicklerDerivationError.enumerationInOneOfUsingField(Type[A].plainPrint))
            case _                       => fail(PicklerDerivationError.notASealedHierarchy(Type[A].plainPrint, macroName))
          }
          entries <- parseOneOfMapping[A, V](mapping)
          overrides <- entries.foldLeft(MIO.pure(Map.empty[String, String])) { case (acc, (key, child)) =>
            acc.flatMap { m =>
              // `asString(key)` is beta-reduced and the *body* evaluated, rather than evaluating the lambda and
              // calling it: Hearth materialises an evaluated lambda as a reflective proxy that cannot be applied.
              val applied = betaReduce[V, String](asString, key)
              import child.Underlying as Child
              val leaf = typeKey[Child]
              applied.semiEval.left
                .flatMap(reasons => constantInterpolation(applied).toRight(reasons))
                .fold(
                  reasons =>
                    fail(PicklerDerivationError.oneOfMappingNotStatic(s"${applied.plainPrint}: ${reasons.toVector.mkString("; ")}")),
                  value =>
                    if (m.contains(leaf))
                      fail(PicklerDerivationError.ambiguousOneOfMapping(Type[A].plainPrint, s"$leaf is mapped more than once"))
                    else if (m.values.exists(_ == value))
                      fail(PicklerDerivationError.ambiguousOneOfMapping(Type[A].plainPrint, s"several cases map to '$value'"))
                    else MIO.pure(m + (leaf -> value))
                )
            }
          }
          _ <- {
            val unmapped = leaves.map { case (_, leaf) => import leaf.Underlying as Leaf; typeKey[Leaf] }.filterNot(overrides.contains)
            if (unmapped.isEmpty) MIO.pure(()) else fail(PicklerDerivationError.incompleteOneOfMapping(Type[A].plainPrint, unmapped))
          }
          _ <- Log.info(s"Discriminator values from oneOfUsingField: ${overrides.mkString("{", ", ", "}")}")
          // The leaves' schemas and codecs must be derived here, with the overridden tags; a `given Pickler[Leaf]` in
          // scope would otherwise be picked up and write the configuration-derived tag instead.
          env <- environment(configExpr, root = typeKey[A], leafNameOverrides = overrides, alsoExclude = overrides.keySet)
          schema <- deriveSchemaRecursively[A](cache, env)
          codec <- deriveCodec[A](env)
        } yield (schema, codec)
      }
      Expr.quote(PicklerFactories.instance[A](Expr.splice(schemaExpr), Expr.splice(codecExpr)))
    }
  }

  private def fail[T](error: PicklerDerivationError): MIO[T] = Log.error(error.getMessage) >> MIO.fail(error)

  /** `(key, leaf type)` for every `key -> pickler` / `(key, pickler)` element of the mapping.
    *
    * Done with Hearth's `DestructuredExpr` so that both tuple spellings decompose the same way: the key is the single argument applied to
    * the receiver (`ArrowAssoc(key)`) or the first of two applied to `Tuple2.apply`; the leaf type is the static type argument of the
    * pickler expression.
    */
  private def parseOneOfMapping[A: Type, V: Type](mapping: VarArgs[(V, Pickler[? <: A])]): MIO[List[(Expr[V], ??)]] = {
    import DestructuredExpr.MethodCall.{AppliedInstance, AppliedValues}
    val PicklerCtor = Type.Ctor1.of[Pickler]

    def argsOf(node: DestructuredExpr): List[DestructuredExpr] = node match {
      case mc: DestructuredExpr.MethodCall =>
        mc.applied.flatMap {
          case ai: AppliedInstance => ai.value match { case r: DestructuredExpr.MethodCall => argsOf(r); case _ => Nil }
          case av: AppliedValues   => av.args
          case _                   => Nil
        }
      case _ => Nil
    }

    def entry(node: DestructuredExpr): Either[String, (Expr[V], ??)] = argsOf(node) match {
      case List(key, pickler) =>
        import pickler.tpe.Underlying as P
        PicklerCtor.unapply(Type[P]) match {
          case Some(leaf) => Right((key.toUntypedExpr.asTyped[V], leaf.Underlying.as_??))
          case None       => Left(s"expected a Pickler, got ${Type[P].plainPrint}")
        }
      case _ => Left(s"expected `key -> pickler`, got ${node.plainPrint}")
    }

    val elements: List[Expr[(V, Pickler[? <: A])]] = mapping.toList
    val parsed = elements.map(element => entry(DestructuredExpr.parseUntyped(element.asUntyped)))
    parsed.collectFirst { case Left(reason) => reason } match {
      case Some(reason) => fail(PicklerDerivationError.oneOfMappingNotStatic(reason))
      case None         => MIO.pure(parsed.collect { case Right(e) => e })
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Runner
  // ---------------------------------------------------------------------------------------------------------------

  /** The plumbing every entry point shares: the `Nothing`/`Any` guard, the log scope, the `ValDefsCache` and its `toValDefs.use` wrapper,
    * loading of the standard extensions, and `runToExprOrFail` with the rendering flags and timeout.
    *
    * `body` receives the cache and a `runSafe`, derives whatever it derives, and returns the final expression; every hoisted definition
    * ends up in scope around it.
    */
  private def runDerivation[A: Type, Out: Type](macroName: String, what: String)(
      body: (MLocal[ValDefsCache], RunSafe[MIO]) => Expr[Out]
  ): Expr[Out] = {
    // On Scala 3 an unconstrained type parameter is inferred as `Any`, which is almost never what the user meant.
    if (Type[A] =:= Type.of[Nothing].asInstanceOf[Type[A]] || Type[A] =:= Type.of[Any].asInstanceOf[Type[A]])
      Environment.reportErrorAndAbort(
        s"$macroName: type parameter was inferred as ${Type[A].prettyPrint}, which is likely unintended.\n" +
          s"Provide an explicit type parameter, e.g.: $macroName[MyType]"
      )

    val rendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender

    Log
      .namedScope(s"Deriving $what for ${Type[A].prettyPrint} at: ${Environment.currentPosition.prettyPrint}") {
        MIO.scoped { runSafe =>
          val cache = ValDefsCache.mlocal
          // Registers the providers behind `IsCollection`, `IsMap`, `IsOption` etc.; without them those extractors never match.
          val _ = runSafe(Environment.loadStandardExtensions().toMIO(allowFailures = false))
          // The schema is derived before the codec, sequentially: the two share Hearth-internal MLocal state, and
          // parallelising them has previously produced silently wrong codegen for parameterised Scala 3 enums in a
          // comparable derivation.
          val result = body(cache, runSafe)
          val vals = runSafe(cache.get)
          vals.toValDefs.use(_ => result)
        }
      }
      .flatTap(result => Log.info(s"Derived final $what: ${result.prettyPrint}"))
      .runToExprOrFail(macroName, infoRendering = rendering, errorRendering = rendering, timeout = derivationTimeout)(
        renderDerivationErrorMessage
      )
  }

  /** The [[DerivationEnv]] for one expansion: the folded configuration, plus the root (and anything else the caller names) excluded from
    * user-pickler lookup.
    */
  private def environment(
      configExpr: Expr[PicklerConfiguration],
      root: String,
      leafNameOverrides: Map[String, String] = Map.empty,
      alsoExclude: Set[String] = Set.empty
  ): MIO[DerivationEnv] =
    foldConfiguration(configExpr).map(config => DerivationEnv(config, leafNameOverrides, alsoExclude + root))

  // ---------------------------------------------------------------------------------------------------------------
  // Configuration
  // ---------------------------------------------------------------------------------------------------------------

  /** Fold the `PicklerConfiguration` expression to a value.
    *
    * Both halves *need* the value: `toEncodedName` and `toDiscriminatorValue` are invoked during expansion and the results spliced as
    * literals (see [[NameSupport]]). `semiEval` handles the common shapes directly (`PicklerConfiguration.default`, `.with*` chains,
    * lambdas such as `_.toUpperCase`). When the expression is merely a reference to a `given`/`implicit val` — which is what an implicit
    * parameter usually is — the definition is followed to its right-hand side and that is evaluated instead, as far as the tree is
    * available.
    */
  private def foldConfiguration(configExpr: Expr[PicklerConfiguration]): MIO[PicklerConfiguration] = {
    implicit val ConfigT: Type[PicklerConfiguration] = PTypes.Config

    def attempt(expr: Expr[PicklerConfiguration], depth: Int): Either[String, PicklerConfiguration] =
      dropNamedArgs(expr).semiEval match {
        case Right(value)  => Right(value)
        case Left(reasons) =>
          dereferenceStable(expr) match {
            case Some(rhs) if depth < 8 => attempt(rhs, depth + 1)
            case _                      => Left(reasons.toVector.mkString("; "))
          }
      }

    attempt(configExpr, 0) match {
      case Right(value) => Log.info(s"Configuration folded at compile time: $value") >> MIO.pure(value)
      case Left(reason) => fail(PicklerDerivationError.configurationNotStatic(configExpr.plainPrint, reason))
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Schema chain entry
  // ---------------------------------------------------------------------------------------------------------------

  /** Entry into the schema rule pipeline.
    *
    * The `inProgress` set is created here, once per expansion, so that the recursion guard has the same lifetime as the `ValDefsCache` it
    * cooperates with.
    */
  private def deriveSchemaRecursively[A: Type](cache: MLocal[ValDefsCache], env: DerivationEnv): MIO[Expr[Schema[A]]] = {
    val inProgress: MLocal[Set[String]] = MLocal(Set.empty[String])(identity)((a, b) => a ++ b)
    deriveSchemaFor[A](using SchemaCtx(Type[A], env, cache, inProgress))
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Diagnostics
  // ---------------------------------------------------------------------------------------------------------------

  /** Logging is enabled either by importing `sttp.tapir.json.pickler.debug.logDerivationForPickler` or by the scalac option
    * `-Xmacro-settings:tapirPickler.logDerivation=true`. A `lazy val`: the answer cannot change within one expansion, and the implicit
    * search is not free.
    */
  lazy val shouldWeLogDerivation: Boolean = {
    implicit val LogDerivationT: Type[Pickler.LogDerivation] = PTypes.LogDerivation
    def importedIntoScope = Expr.summonImplicit[Pickler.LogDerivation].isDefined
    def setGlobally = (for {
      data <- Environment.typedSettings.toOption
      namespace <- data.get(derivationSettingsNamespace)
      shouldLog <- namespace.get("logDerivation").flatMap(_.asBoolean)
    } yield shouldLog).getOrElse(false)

    importedIntoScope || setGlobally
  }

  private def renderDerivationErrorMessage(errorLogs: String, errors: NonEmptyVector[Throwable]): String = {
    val errorsRendered = errors
      .map { e =>
        val msg = Option(e.getMessage).getOrElse(e.getClass.getName)
        msg.split("\n").toList match {
          case head :: tail => (("  - " + head) :: tail.map("    " + _)).mkString("\n")
          case _            => "  - " + msg
        }
      }
      .mkString("\n")
    val hint =
      "Enable debug logging with: import sttp.tapir.json.pickler.debug.logDerivationForPickler " +
        s"or the scalac option -Xmacro-settings:$derivationSettingsNamespace.logDerivation=true"
    if (errorLogs.nonEmpty)
      s"""Pickler derivation failed with the following errors:
         |$errorsRendered
         |and the following logs:
         |$errorLogs
         |$hint""".stripMargin
    else
      s"""Pickler derivation failed with the following errors:
         |$errorsRendered
         |$hint""".stripMargin
  }
}

private[pickler] object PicklerMacros {

  /** Number of pickler derivations currently on the stack of this compiler thread.
    *
    * A derivation summons `Pickler[X]` for every nested `X` (see [[ImplicitPicklerSupport]]). If `generic.auto` is in scope, one candidate
    * is `Pickler.derived[X]` itself, i.e. this macro, expanded *while* the outer one is running. `derivePicklerImpl` refuses to run in that
    * situation, which turns the candidate into a failed one and lets the search fall through to "no user-supplied pickler". A `ThreadLocal`
    * rather than a plain `var` only because nothing guarantees the compiler will never expand macros on several threads.
    */
  private val depth: ThreadLocal[Int] = ThreadLocal.withInitial(() => 0)

  private def nested[R](body: => R): R = {
    depth.set(depth.get + 1)
    try body
    finally depth.set(depth.get - 1)
  }

  def derivePicklerImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Pickler[A]] =
    if (depth.get > 0)
      // Reached only as an implicit candidate during another derivation's `summon[Pickler[X]]`. Aborting here is
      // what makes that summon fail cleanly instead of deriving `X` a second time (or forever, for cyclic types).
      q.reflect.report.errorAndAbort(
        s"Pickler.derived[${q.reflect.TypeRepr.of[A].show}] invoked from within another Pickler derivation; " +
          "nested types are derived structurally unless a user-defined Pickler is in scope."
      )
    else nested(new PicklerMacros(q).derivePickler[A](config))

  def deriveSchemaOnlyImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Schema[A]] =
    nested(new PicklerMacros(q).deriveSchemaOnly[A](config))

  def derivedEnumerationImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[CreateDerivedEnumerationPickler[A]] =
    nested(new PicklerMacros(q).deriveEnumerationBuilder[A](config))

  def oneOfUsingFieldImpl[A: Type, V: Type](
      extractor: Expr[A => V],
      asString: Expr[V => String],
      mapping: Expr[Seq[(V, Pickler[? <: A])]],
      config: Expr[PicklerConfiguration]
  )(using q: Quotes): Expr[Pickler[A]] =
    nested(new PicklerMacros(q).deriveOneOfUsingField[A, V](extractor, asString, mapping, config))
}

/** [[PlatformSupport]] for Scala 3.
  *
  * The two mapper shapes are pinned by jsoniter's `CompileTimeEval` (`NameMapper.scala` in `jsoniter-scala-macros`):
  *   - `evalApplyStringTerm` destructures the argument with the `Lambda(params, body)` extractor and evaluates the body as a `Match` with a
  *     `null` default, so a `Closure` over a `DefDef` whose body is a bare `Match` on `Literal` patterns is the shape to produce. This is
  *     exactly what the typer produces for `{ case "a" => "b" }` before `ExpandSAMs`, which is why hand-written jsoniter configurations
  *     work.
  *   - A `Map(...)` literal is matched by a quote pattern that does **not** see through the `Inlined` nodes splicing produces, so it is
  *     unusable from a macro.
  */
private[compiletime] trait PlatformSupportScala3 extends PlatformSupport { this: MacroCommonsScala3 =>
  import quotes.reflect.*

  private def stringMatch(pairs: List[(String, String)], scrutinee: Term, fallback: Option[Term]): Match =
    Match(
      scrutinee,
      pairs.map { case (k, v) => CaseDef(Literal(StringConstant(k)), None, Literal(StringConstant(v))) } ++
        fallback.map(f => CaseDef(Wildcard(), None, f)).toList
    )

  private val stringToString: MethodType =
    MethodType(List("x"))(_ => List(TypeRepr.of[String]), _ => TypeRepr.of[String])

  protected def stringPartialFunction(pairs: List[(String, String)]): Expr[PartialFunction[String, String]] = {
    val sym = Symbol.newMethod(Symbol.spliceOwner, "picklerFieldNameMapper", stringToString)
    val defDef = DefDef(
      sym,
      {
        case List(List(x: Term)) => Some(stringMatch(pairs, x, fallback = None))
        case other               => throw new IllegalStateException(s"unexpected lambda parameters: $other")
      }
    )
    Block(List(defDef), Closure(Ref(sym), Some(TypeRepr.of[PartialFunction[String, String]])))
      .asExprOf[PartialFunction[String, String]]
  }

  // No wildcard case on purpose. jsoniter's `evalApplyStringTerm` evaluates a lambda body with a `null` default, and a `None` from
  // the leaf mapper makes `JsonCodecMaker` fail with "Discriminator is not defined for ...". Every leaf we know of is in `pairs`, so
  // reaching the default means `jsoniterLeafName` no longer agrees with jsoniter's own naming -- which must be a compile error, not a
  // silently different discriminator value from the one the schema documents.
  protected def stringFunction(pairs: List[(String, String)]): Expr[String => String] =
    Lambda(
      Symbol.spliceOwner,
      stringToString,
      {
        case (_, List(x: Term)) => stringMatch(pairs, x, fallback = None)
        case (_, other)         => throw new IllegalStateException(s"unexpected lambda parameters: $other")
      }
    ).asExprOf[String => String]

  /** Mirrors `JsonCodecMaker.discriminatorValue` in `jsoniter-scala-macros`: enum values are named by their term symbol, everything else by
    * its type symbol, and a module's trailing `$` is dropped. Coupled to `Versions.jsoniter`: if jsoniter changes its naming, the leaf
    * mapper (`stringFunction`) stops matching and `JsonCodecMaker` fails compilation with "Discriminator is not defined".
    */
  protected def jsoniterLeafName[A: Type]: String = {
    val tpe = TypeRepr.of[A]
    val symbol = if (tpe.termSymbol.flags.is(Flags.Enum)) tpe.termSymbol else tpe.typeSymbol
    val name = symbol.fullName
    if (symbol.flags.is(Flags.Module)) name.substring(0, name.length - 1) else name
  }

  protected def tapirFullName[A: Type]: String = SNameMacros.typeFullNameFromTpe(TypeRepr.of[A])

  protected def flattenedTypeArguments[A: Type]: List[String] = SNameMacros.extractTypeArguments(TypeRepr.of[A].dealias)

  protected def typeKey[A: Type]: String = {
    def deepDealias(tpe: TypeRepr): TypeRepr = tpe.dealias match {
      case AppliedType(tycon, args) => AppliedType(tycon, args.map(deepDealias))
      case other                    => other
    }
    deepDealias(TypeRepr.of[A]).show
  }

  protected def implicitLazyVals[Out: Type](vals: List[(String, UntypedType, List[UntypedExpr] => UntypedExpr)])(
      body: List[UntypedExpr] => Expr[Out]
  ): Expr[Out] = {
    val syms = vals.map { case (name, tpe, _) =>
      Symbol.newVal(Symbol.spliceOwner, name, tpe, Flags.Implicit | Flags.Lazy, Symbol.noSymbol)
    }
    val refs: List[UntypedExpr] = syms.map(Ref(_))
    val defs = vals.zip(syms).map { case ((_, _, rhs), sym) => ValDef(sym, Some(rhs(refs).changeOwner(sym))) }
    Block(defs, body(refs).asTerm).asExprOf[Out]
  }

  protected def betaReduce[A: Type, B: Type](f: Expr[A => B], a: Expr[A]): Expr[B] = {
    val application = '{ $f($a) }
    Term.betaReduce(application.asTerm).fold(application)(_.asExprOf[B])
  }

  protected def constantInterpolation(expr: Expr[String]): Option[String] = {
    def constant(term: Term): Option[Any] = term match {
      case Inlined(_, Nil, inner) => constant(inner)
      case Typed(inner, _)        => constant(inner)
      case Literal(c)             => Some(c.value)
      case _                      => None
    }
    expr match {
      case '{ StringContext(${ Varargs(parts) }*).s(${ Varargs(args) }*) } =>
        for {
          ps <- parts.foldRight(Option(List.empty[String]))((p, acc) => acc.flatMap(tail => p.value.map(_ :: tail)))
          as <- args.foldRight(Option(List.empty[Any]))((a, acc) => acc.flatMap(tail => constant(a.asTerm).map(_ :: tail)))
        } yield StringContext(ps*).s(as*)
      case _ => None
    }
  }

  protected def dropNamedArgs[A: Type](expr: Expr[A]): Expr[A] = {
    val transform = new TreeMap {
      override def transformTerm(tree: Term)(owner: Symbol): Term = tree match {
        case NamedArg(_, arg) => transformTerm(arg)(owner)
        case other            => super.transformTerm(other)(owner)
      }
    }
    transform.transformTerm(expr.asTerm)(Symbol.spliceOwner).asExprOf[A]
  }

  protected def dereferenceStable[A: Type](expr: Expr[A]): Option[Expr[A]] = {
    def loop(term: Term): Option[Term] = term match {
      case Inlined(_, Nil, inner)          => loop(inner)
      case Typed(inner, _)                 => loop(inner)
      case ref: Ref if ref.symbol.isValDef =>
        ref.symbol.tree match {
          case ValDef(_, _, Some(rhs)) => Some(rhs)
          case _                       => None
        }
      case _ => None
    }
    loop(expr.asTerm).map(_.asExprOf[A])
  }
}
