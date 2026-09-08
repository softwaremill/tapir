package sttp.tapir.json.pickler.next.internal.compiletime

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.MacroCommons
import hearth.fp.data.NonEmptyVector
import hearth.fp.effect.*
import hearth.std.*
import sttp.tapir.Schema
import sttp.tapir.json.pickler.next.{Pickler, PicklerConfiguration}
import sttp.tapir.json.pickler.next.internal.runtime.PicklerFactories

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
trait PicklerMacrosImpl extends DerivationTimeout with AnnotationSupport with SchemaDerivation with CodecDerivation {
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
    lazy val Config: Type[PicklerConfiguration] = Type.of[PicklerConfiguration]
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
