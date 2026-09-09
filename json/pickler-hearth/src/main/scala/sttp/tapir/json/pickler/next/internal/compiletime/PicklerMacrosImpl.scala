package sttp.tapir.json.pickler.next.internal.compiletime

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.MacroCommons
import hearth.fp.data.NonEmptyVector
import hearth.fp.effect.*
import hearth.std.*
import sttp.tapir.Schema
import sttp.tapir.json.pickler.next.{Pickler, PicklerConfiguration}
import sttp.tapir.json.pickler.next.internal.runtime.{PicklerFactories, PicklerUtils}

/** Core, platform-independent derivation logic for [[Pickler]].
  *
  * This trait holds no `Quotes`/`Context` of its own — it is mixed into the platform bundle (`PicklerMacros`), which
  * supplies Hearth's cake and the [[PlatformSupport]] implementation. That separation is what allows the same logic
  * to be reused from a Scala 2 macro bundle later without touching this file.
  *
  * ==Structure==
  * The schema half ([[SchemaDerivation]]) walks the type graph and hoists one `lazy val` per schema into a shared
  * `ValDefsCache`. The codec half ([[CodecDerivation]]) walks the same graph and emits one `JsonCodecMaker.make` per
  * case class / sealed hierarchy, configured from the *same* `PicklerConfiguration`. A single `toValDefs.use` wraps
  * the whole instance expression, so every hoisted definition sits at instance scope and is built once.
  */
trait PicklerMacrosImpl
    extends DerivationTimeout
    with AnnotationSupport
    with ImplicitPicklerSupport
    with SchemaDerivation
    with CodecDerivation {
  this: MacroCommons & StdExtensions & LoadStandardExtensionsOnce & PlatformSupport =>

  override protected def derivationSettingsNamespace: String = "tapirPickler"

  /** Centralised `Type.of[...]` instances.
    *
    * These must not be written as `implicit val`s in the scope where they are also the implicit being summoned: Hearth
    * resolves `Type[A]` implicits lazily via cross-quotes, so a self-referential definition causes a stack overflow at
    * macro-expansion time with no usable stack trace. Keeping every `Type.of` behind a method/lazy val on this object,
    * and assigning it to a local `implicit val` at each use site, avoids that.
    */
  private[compiletime] object PTypes {
    def SchemaOf[A: Type]: Type[Schema[A]] = Type.of[Schema[A]]
    def PicklerOf[A: Type]: Type[Pickler[A]] = Type.of[Pickler[A]]
    def CodecOf[A: Type]: Type[JsonValueCodec[A]] = Type.of[JsonValueCodec[A]]
    def MappingEntryOf[A: Type, V: Type]: Type[(V, Pickler[? <: A])] = Type.of[(V, Pickler[? <: A])]
    def FnOf[A: Type, B: Type]: Type[A => B] = Type.of[A => B]
    lazy val Config: Type[PicklerConfiguration] = Type.of[PicklerConfiguration]
    lazy val Configuration: Type[sttp.tapir.generic.Configuration] = Type.of[sttp.tapir.generic.Configuration]
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
    val macroName = "Pickler.derived"

    // On Scala 3 an unconstrained type parameter is inferred as `Any`, which is almost never what the user meant.
    if (Type[A] =:= Type.of[Nothing].asInstanceOf[Type[A]] || Type[A] =:= Type.of[Any].asInstanceOf[Type[A]])
      Environment.reportErrorAndAbort(
        s"$macroName: type parameter was inferred as ${Type[A].prettyPrint}, which is likely unintended.\n" +
          s"Provide an explicit type parameter, e.g.: $macroName[MyType]"
      )

    // Extracted before any Expr.quote: `??` is a macro-internal existential and must not leak into a reified tree.
    val selfType: Option[??] = Some(Type[A].as_??)
    implicitLookupExclusions += Type[A].plainPrint

    Log
      .namedScope(s"Deriving Pickler for ${Type[A].prettyPrint} at: ${Environment.currentPosition.prettyPrint}") {
        MIO.scoped { runSafe =>
          val cache = ValDefsCache.mlocal

          // The schema is derived first and sequentially: schema and codec derivations share Hearth-internal MLocal
          // state, and parallelising them has previously produced silently wrong codegen for parameterised Scala 3
          // enums in a comparable derivation (plan §6.2).
          val (schemaExpr, codecExpr) = runSafe {
            for {
              _ <- ensureStandardExtensionsLoaded()
              schema <- deriveSchemaRecursively[A](cache, configExpr, selfType)
              config <- foldConfiguration(configExpr)
              codec <- deriveCodec[A](config)
            } yield (schema, codec)
          }

          val vals = runSafe(cache.get)
          vals.toValDefs.use { _ =>
            Expr.quote(PicklerFactories.instance[A](Expr.splice(schemaExpr), Expr.splice(codecExpr)))
          }
        }
      }
      .flatTap(result => Log.info(s"Derived final Pickler: ${result.prettyPrint}"))
      .runToExprOrFail(
        macroName,
        infoRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
        errorRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
        timeout = derivationTimeout
      )(renderDerivationErrorMessage)
  }

  /** Schema-only entry point. Needs no compile-time configuration: the schema chain applies `toEncodedName` /
    * `toDiscriminatorValue` at runtime, so there is nothing to fold.
    */
  def deriveSchemaOnly[A: Type](configExpr: Expr[PicklerConfiguration]): Expr[Schema[A]] = {
    val selfType: Option[??] = Some(Type[A].as_??)
    implicitLookupExclusions += Type[A].plainPrint

    Log
      .namedScope(s"Deriving Schema for ${Type[A].prettyPrint} at: ${Environment.currentPosition.prettyPrint}") {
        MIO.scoped { runSafe =>
          val cache = ValDefsCache.mlocal
          val schemaExpr = runSafe {
            for {
              _ <- ensureStandardExtensionsLoaded()
              result <- deriveSchemaRecursively[A](cache, configExpr, selfType)
            } yield result
          }
          val vals = runSafe(cache.get)
          vals.toValDefs.use(_ => schemaExpr)
        }
      }
      .flatTap(result => Log.info(s"Derived final Schema: ${result.prettyPrint}"))
      .runToExprOrFail(
        "Pickler.schemaFor",
        infoRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
        errorRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
        timeout = derivationTimeout
      )(renderDerivationErrorMessage)
  }

  /** `Pickler.oneOfUsingField[A, V](extractor, asString)(v1 -> pickler1, ...)`.
    *
    * The discriminator value of each mapped leaf is `asString(v)`, decided by the user rather than by the
    * configuration. Both halves get it the same way:
    *   - the **schema** is core's own `Schema.oneOfUsingField`, fed the children's schemas (so a user's child pickler
    *     is fully honoured on the documentation side);
    *   - the **codec** is the ordinary `CodecDerivation` run with `leafNameOverrides` set to `asString(v)` per leaf.
    *     jsoniter needs those values as literals, which is why the keys and `asString` are evaluated at expansion
    *     time, and why the children's *codecs* are derived afresh rather than taken from the child picklers: a
    *     user-derived leaf codec would write its configuration-derived tag, not the overridden one.
    */
  def deriveOneOfUsingField[A: Type, V: Type](
      extractor: Expr[A => V],
      asString: Expr[V => String],
      mapping: VarArgs[(V, Pickler[? <: A])],
      configExpr: Expr[PicklerConfiguration]
  ): Expr[Pickler[A]] = {
    implicit val SchemaA: Type[Schema[A]] = PTypes.SchemaOf[A]
    implicit val SchemaV: Type[Schema[V]] = PTypes.SchemaOf[V]
    implicit val PicklerA: Type[Pickler[A]] = PTypes.PicklerOf[A]
    implicit val CodecA: Type[JsonValueCodec[A]] = PTypes.CodecOf[A]
    implicit val EntryT: Type[(V, Pickler[? <: A])] = PTypes.MappingEntryOf[A, V]
    implicit val ExtractorT: Type[A => V] = PTypes.FnOf[A, V]
    implicit val StringT: Type[String] = PTypes.StringT
    implicit val AsStringT: Type[V => String] = PTypes.FnOf[V, String]
    implicit val ConfigT: Type[PicklerConfiguration] = PTypes.Config
    implicit val ConfigurationT: Type[sttp.tapir.generic.Configuration] = PTypes.Configuration
    val macroName = "Pickler.oneOfUsingField"

    Log
      .namedScope(s"Deriving Pickler for ${Type[A].prettyPrint} with oneOfUsingField at: ${Environment.currentPosition.prettyPrint}") {
        MIO.scoped { runSafe =>
          val cache = ValDefsCache.mlocal

          val (schemaExpr, codecExpr) = runSafe {
            for {
              _ <- ensureStandardExtensionsLoaded()
              _ <- Enum.parse[A].toEither match {
                case Right(_)    => MIO.pure(())
                case Left(_)     => fail(PicklerDerivationError.NotASealedHierarchy(Type[A].plainPrint, macroName))
              }
              entries <- parseOneOfMapping[A, V](mapping)
              overrides <- entries.foldLeft(MIO.pure(Map.empty[String, String])) { case (acc, (key, child)) =>
                acc.flatMap { m =>
                  // `asString(key)` is beta-reduced and the *body* evaluated, rather than evaluating the lambda and
                  // calling it: Hearth materialises an evaluated lambda as a reflective proxy that cannot be applied.
                  val applied = betaReduce[V, String](asString, key)
                  applied.semiEval.left.flatMap(reasons => constantInterpolation(applied).toRight(reasons)).fold(
                    reasons =>
                      fail(PicklerDerivationError.OneOfMappingNotStatic(s"${applied.plainPrint}: ${reasons.toVector.mkString("; ")}")),
                    s => MIO.pure(m + (child.Underlying.plainPrint -> s))
                  )
                }
              }
              _ <- Log.info(s"Discriminator values from oneOfUsingField: ${overrides.mkString("{", ", ", "}")}")
              _ = {
                leafNameOverrides = overrides
                // The mapped leaves' codecs must be derived here, with the overridden tags; a `given Pickler[Leaf]` in
                // scope would otherwise be picked up and write the configuration-derived tag instead.
                implicitLookupExclusions ++= overrides.keySet + Type[A].plainPrint
              }
              schemaV <- Expr.summonImplicit[Schema[V]].toOption match {
                case Some(s) => MIO.pure(s)
                case None    => fail(PicklerDerivationError.OneOfMappingNotStatic(s"no implicit Schema[${Type[V].plainPrint}] for the discriminator"))
              }
              config <- foldConfiguration(configExpr)
              codec <- deriveCodec[A](config)
            } yield {
              // `VarArgs.from` re-packs the elements as an `Expr[Seq[_]]` on both platforms, which is the one shape
              // cross-quotes can splice with `*`.
              val mappingSeq = VarArgs.from(mapping.toList)
              val schema = Expr.quote {
                Schema.oneOfUsingField[A, V](Expr.splice(extractor), Expr.splice(asString))(
                  PicklerUtils.oneOfSchemas[A, V](Expr.splice(mappingSeq)*)*
                )(Expr.splice(configExpr).genericDerivationConfig, Expr.splice(schemaV))
              }
              (schema, codec)
            }
          }

          val vals = runSafe(cache.get)
          vals.toValDefs.use { _ =>
            Expr.quote(PicklerFactories.instance[A](Expr.splice(schemaExpr), Expr.splice(codecExpr)))
          }
        }
      }
      .flatTap(result => Log.info(s"Derived final Pickler: ${result.prettyPrint}"))
      .runToExprOrFail(
        macroName,
        infoRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
        errorRendering = if (shouldWeLogDerivation) RenderFrom(Log.Level.Info) else DontRender,
        timeout = derivationTimeout
      )(renderDerivationErrorMessage)
  }

  private def fail[T](error: PicklerDerivationError): MIO[T] = Log.error(error.message) >> MIO.fail(error)

  /** `(key, leaf type)` for every `key -> pickler` / `(key, pickler)` element of the mapping.
    *
    * Done with Hearth's `DestructuredExpr` so that both tuple spellings, and both Scala versions, decompose the same
    * way: the key is the single argument applied to the receiver (`ArrowAssoc(key)`) or the first of two applied to
    * `Tuple2.apply`; the leaf type is the static type argument of the pickler expression.
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

    // `Expr[Seq[X]]` *is* Hearth's `VarArgs[X]` on Scala 3 (and `Seq[Expr[X]]` on Scala 2), so this split is what
    // makes the element-wise parse cross-platform.
    val elements: List[Expr[(V, Pickler[? <: A])]] = mapping.toList
    val parsed = elements.map(element => entry(DestructuredExpr.parseUntyped(element.asUntyped)))
    parsed.collectFirst { case Left(reason) => reason } match {
      case Some(reason) => fail(PicklerDerivationError.OneOfMappingNotStatic(reason))
      case None         => MIO.pure(parsed.collect { case Right(e) => e })
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Configuration
  // ---------------------------------------------------------------------------------------------------------------

  /** Fold the `PicklerConfiguration` expression to a value.
    *
    * The codec half *needs* the value: `toEncodedName` and `toDiscriminatorValue` are invoked during expansion and the
    * results handed to `JsonCodecMaker` as literals (see [[CodecDerivation]]). `semiEval` handles the common shapes
    * directly (`PicklerConfiguration.default`, `.with*` chains, lambdas such as `_.toUpperCase`). When the expression
    * is merely a reference to a `given`/`implicit val` — which is what an implicit parameter usually is — the
    * definition is followed to its right-hand side and that is evaluated instead, as far as the tree is available.
    */
  private def foldConfiguration(configExpr: Expr[PicklerConfiguration]): MIO[PicklerConfiguration] = {
    implicit val ConfigT: Type[PicklerConfiguration] = PTypes.Config

    def attempt(expr: Expr[PicklerConfiguration], depth: Int): Either[String, PicklerConfiguration] =
      expr.semiEval match {
        case Right(value) => Right(value)
        case Left(reasons) =>
          dereferenceStable(expr) match {
            case Some(rhs) if depth < 8 => attempt(rhs, depth + 1)
            case _                      => Left(reasons.toVector.mkString("; "))
          }
      }

    attempt(configExpr, 0) match {
      case Right(value) => Log.info(s"Configuration folded at compile time: $value") >> MIO.pure(value)
      case Left(reason) =>
        val error = PicklerDerivationError.ConfigurationNotStatic(configExpr.plainPrint, reason)
        Log.error(error.message) >> MIO.fail(error)
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Schema chain entry
  // ---------------------------------------------------------------------------------------------------------------

  /** Entry into the schema rule pipeline.
    *
    * The `inProgress` set is created here, once per expansion, so that the recursion guard has the same lifetime as
    * the `ValDefsCache` it cooperates with.
    */
  private def deriveSchemaRecursively[A: Type](
      cache: MLocal[ValDefsCache],
      configExpr: Expr[PicklerConfiguration],
      selfType: Option[??]
  ): MIO[Expr[Schema[A]]] = {
    val inProgress: MLocal[Set[String]] = MLocal(Set.empty[String])(identity)((a, b) => a ++ b)
    deriveSchemaFor[A](using SchemaCtx(Type[A], configExpr, cache, inProgress, selfType))
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Diagnostics
  // ---------------------------------------------------------------------------------------------------------------

  /** Logging is enabled either by importing `sttp.tapir.json.pickler.next.debug.logDerivationForPickler` or by the scalac
    * option `-Xmacro-settings:tapirPickler.logDerivation=true`.
    */
  def shouldWeLogDerivation: Boolean = {
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
      "Enable debug logging with: import sttp.tapir.json.pickler.next.debug.logDerivationForPickler " +
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
