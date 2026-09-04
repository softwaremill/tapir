package sttp.tapir.json.pickler.next.internal.compiletime

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter}
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
  * supplies Hearth's cake. That separation is what allows the same logic to be reused from a Scala 2 macro bundle
  * later without touching this file.
  *
  * ==Phase 0 status==
  * The plumbing is complete and load-bearing: one shared `ValDefsCache`, the encode/decode bodies forward-declared as
  * `def`s before their bodies are derived (which is what breaks recursion and keeps each `Expr.splice` free of
  * cross-splice staging problems), and a single `toValDefs.use` wrapping the *entire* instance expression. The three
  * `derive*Recursively` methods are deliberate stubs; Phase 1+ replaces them with rule pipelines without changing any
  * of the surrounding structure.
  */
trait PicklerMacrosImpl extends DerivationTimeout with AnnotationSupport with SchemaDerivation {
  this: MacroCommons & StdExtensions & LoadStandardExtensionsOnce =>

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
    lazy val Config: Type[PicklerConfiguration] = Type.of[PicklerConfiguration]
    lazy val JsonReaderT: Type[JsonReader] = Type.of[JsonReader]
    lazy val JsonWriterT: Type[JsonWriter] = Type.of[JsonWriter]
    lazy val UnitT: Type[Unit] = Type.of[Unit]
    lazy val LogDerivation: Type[Pickler.LogDerivation] = Type.of[Pickler.LogDerivation]
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Entry points
  // ---------------------------------------------------------------------------------------------------------------

  /** Full type class instance: `Schema` + `JsonValueCodec`, derived together. */
  def derivePickler[A: Type](configExpr: Expr[PicklerConfiguration]): Expr[Pickler[A]] = {
    implicit val SchemaA: Type[Schema[A]] = PTypes.SchemaOf[A]
    implicit val PicklerA: Type[Pickler[A]] = PTypes.PicklerOf[A]

    // Extracted before any Expr.quote: `??` is a macro-internal existential and must not leak into a reified tree.
    val selfType: Option[??] = Some(Type[A].as_??)

    derivePicklerCore[A, Pickler[A]]("Pickler.derived", configExpr, selfType) {
      case (schemaExpr, encodeFn, decodeFn, nullValueExpr) =>
        Expr.quote {
          PicklerFactories.instance[A](
            Expr.splice(schemaExpr),
            Expr.splice(nullValueExpr),
            (in: JsonReader, default: A) => {
              val _ = default
              Expr.splice(decodeFn(Expr.quote(in), configExpr))
            },
            (x: A, out: JsonWriter) => Expr.splice(encodeFn(Expr.quote(x), Expr.quote(out), configExpr))
          )
        }
    }
  }

  /** Schema-only entry point. Shares the whole derivation core; simply discards the codec halves. */
  def deriveSchemaOnly[A: Type](configExpr: Expr[PicklerConfiguration]): Expr[Schema[A]] = {
    val selfType: Option[??] = Some(Type[A].as_??)

    Log
      .namedScope(s"Deriving Schema for ${Type[A].prettyPrint} at: ${Environment.currentPosition.prettyPrint}") {
        MIO.scoped { runSafe =>
          val cache = ValDefsCache.mlocal
          // No `semiEval` of the configuration here: the schema chain applies `toEncodedName` /
          // `toDiscriminatorValue` at runtime (see `SchemaDerivation`), so there is nothing to fold at compile time
          // and nothing to keep in sync between a folded and an unfolded code path.

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

  // ---------------------------------------------------------------------------------------------------------------
  // Derivation core
  // ---------------------------------------------------------------------------------------------------------------

  /** Derives all four members of a pickler and hands them to `adapt`, which assembles the final expression.
    *
    * Why the callback rather than returning a tuple: `toValDefs.use` has to wrap the *outermost* expression. If it
    * wrapped only, say, the encode lambda, then every cached `lazy val` (including the schema graph, whose
    * construction involves `SName` parsing, field-list building and annotation folding) would become local to that
    * lambda and be rebuilt on every `encodeValue` call. That mistake is silent — no error, no warning — and has
    * previously cost a 6x runtime regression in a comparable derivation. Passing an adapter keeps the `use` call at
    * the one correct place.
    */
  private def derivePicklerCore[A: Type, Out: Type](
      macroName: String,
      configExpr: Expr[PicklerConfiguration],
      selfType: Option[??]
  )(
      adapt: (
          Expr[Schema[A]],
          (Expr[A], Expr[JsonWriter], Expr[PicklerConfiguration]) => Expr[Unit],
          (Expr[JsonReader], Expr[PicklerConfiguration]) => Expr[A],
          Expr[A]
      ) => Expr[Out]
  ): Expr[Out] = {
    // On Scala 3 an unconstrained type parameter is inferred as `Any`, which is almost never what the user meant.
    if (Type[A] =:= Type.of[Nothing].asInstanceOf[Type[A]] || Type[A] =:= Type.of[Any].asInstanceOf[Type[A]])
      Environment.reportErrorAndAbort(
        s"$macroName: type parameter was inferred as ${Type[A].prettyPrint}, which is likely unintended.\n" +
          s"Provide an explicit type parameter, e.g.: $macroName[MyType]"
      )

    Log
      .namedScope(s"Deriving Pickler for ${Type[A].prettyPrint} at: ${Environment.currentPosition.prettyPrint}") {
        implicit val ConfigT: Type[PicklerConfiguration] = PTypes.Config
        implicit val JsonReaderT: Type[JsonReader] = PTypes.JsonReaderT
        implicit val JsonWriterT: Type[JsonWriter] = PTypes.JsonWriterT
        implicit val UnitT: Type[Unit] = PTypes.UnitT

        MIO.scoped { runSafe =>
          // One cache shared by the schema `lazy val`s and the encode/decode `def`s, so that a single
          // `toValDefs.use` emits them all side by side at instance scope.
          val cache = ValDefsCache.mlocal

          // Fold the configuration to a value at expansion time when possible. This matters more here than for a
          // plain codec: the *shape* of a tapir Schema depends on the configuration, so a statically known config
          // lets us bake field names and discriminators in as constants.
          //
          // Hearth has a known limitation where `semiEval` fails on an `Inlined(...)` tree wrapping a module field
          // access -- which is exactly the shape of the most common call site, `PicklerConfiguration.default`. The
          // outcome is logged rather than assumed, because whether it succeeds decides which codegen path every rule
          // takes, and a silent difference between the two is a nasty class of bug.
          val evConfig: Option[PicklerConfiguration] = configExpr.semiEval.toOption
          runSafe(
            Log.info(
              if (evConfig.isDefined) s"Configuration folded at compile time: ${evConfig.get}"
              else s"Configuration NOT statically known (${configExpr.prettyPrint}); using runtime-checked codegen"
            )
          )

          val schemaMIO: MIO[Expr[Schema[A]]] = deriveSchemaRecursively[A](cache, configExpr, selfType)

          // Encoder body, cached as `def pickler_encode_A(a: A, w: JsonWriter, c: PicklerConfiguration): Unit`.
          // Forward-declaring before deriving the body is what lets a recursive type refer back to this def.
          val encMIO: MIO[(Expr[A], Expr[JsonWriter], Expr[PicklerConfiguration]) => Expr[Unit]] = {
            val defBuilder =
              ValDefBuilder.ofDef3[A, JsonWriter, PicklerConfiguration, Unit](s"pickler_encode_${Type[A].shortName}")
            for {
              _ <- Log.info(s"Forward-declaring encode body for ${Type[A].prettyPrint}")
              _ <- cache.forwardDeclare("pickler-encode-body", defBuilder)
              _ <- MIO.scoped { rs =>
                rs(cache.buildCachedWith("pickler-encode-body", defBuilder) { case (_, (value, writer, config)) =>
                  rs(deriveEncoderRecursively[A](value, writer, config, cache, evConfig, selfType))
                })
              }
              _ <- Log.info(s"Defined encode body for ${Type[A].prettyPrint}")
              fn <- cache.get3Ary[A, JsonWriter, PicklerConfiguration, Unit]("pickler-encode-body")
            } yield fn.get
          }

          // Decoder body, cached as `def pickler_decode_A(r: JsonReader, c: PicklerConfiguration): A`.
          val decMIO: MIO[(Expr[JsonReader], Expr[PicklerConfiguration]) => Expr[A]] = {
            val defBuilder =
              ValDefBuilder.ofDef2[JsonReader, PicklerConfiguration, A](s"pickler_decode_${Type[A].shortName}")
            for {
              _ <- Log.info(s"Forward-declaring decode body for ${Type[A].prettyPrint}")
              _ <- cache.forwardDeclare("pickler-decode-body", defBuilder)
              _ <- MIO.scoped { rs =>
                rs(cache.buildCachedWith("pickler-decode-body", defBuilder) { case (_, (reader, config)) =>
                  rs(deriveDecoderRecursively[A](reader, config, cache, evConfig, selfType))
                })
              }
              _ <- Log.info(s"Defined decode body for ${Type[A].prettyPrint}")
              fn <- cache.get2Ary[JsonReader, PicklerConfiguration, A]("pickler-decode-body")
            } yield fn.get
          }

          val nullMIO: MIO[Expr[A]] = deriveNullValue[A]

          // The codec halves are parallelised so that an unsupported type reports every problem at once instead of
          // one fix-and-retry cycle per method. The schema is composed *sequentially* on purpose: schema and decoder
          // derivations share Hearth-internal MLocal state, and parallelising those two has previously produced
          // silently wrong codegen for parameterised Scala 3 enums in a comparable derivation. Revisit only with the
          // enum tests green, and re-run them straight after.
          val (schemaExpr, ((encFn, decFn), nullVal)) = runSafe {
            for {
              _ <- ensureStandardExtensionsLoaded()
              schema <- schemaMIO
              codec <- encMIO.parTuple(decMIO).parTuple(nullMIO)
            } yield (schema, codec)
          }

          val vals = runSafe(cache.get)
          val resultExpr = adapt(schemaExpr, encFn, decFn, nullVal)
          vals.toValDefs.use(_ => resultExpr)
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

  // ---------------------------------------------------------------------------------------------------------------
  // Phase 0 stubs -- replaced by rule pipelines in Phase 1+
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

  private def deriveEncoderRecursively[A: Type](
      value: Expr[A],
      writer: Expr[JsonWriter],
      config: Expr[PicklerConfiguration],
      cache: MLocal[ValDefsCache],
      evConfig: Option[PicklerConfiguration],
      selfType: Option[??]
  ): MIO[Expr[Unit]] = {
    val _ = (cache, evConfig, selfType, config)
    Log.info(s"[stub] Encoder derivation for ${Type[A].prettyPrint}") >>
      MIO.pure(Expr.quote {
        val _ = Expr.splice(value)
        Expr.splice(writer).writeNull()
      })
  }

  private def deriveDecoderRecursively[A: Type](
      reader: Expr[JsonReader],
      config: Expr[PicklerConfiguration],
      cache: MLocal[ValDefsCache],
      evConfig: Option[PicklerConfiguration],
      selfType: Option[??]
  ): MIO[Expr[A]] = {
    val _ = (cache, evConfig, selfType, config)
    val typeName = Expr(Type[A].plainPrint)
    Log.info(s"[stub] Decoder derivation for ${Type[A].prettyPrint}") >>
      MIO.pure(Expr.quote(PicklerUtils.notImplementedDecode[A](Expr.splice(reader), Expr.splice(typeName))))
  }

  private def deriveNullValue[A: Type]: MIO[Expr[A]] =
    MIO.pure(Expr.quote(PicklerUtils.nullValueOf[A]))

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
