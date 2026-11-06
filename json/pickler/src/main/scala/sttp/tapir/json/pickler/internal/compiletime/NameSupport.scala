package sttp.tapir.json.pickler.internal.compiletime

import hearth.MacroCommons
import sttp.tapir.Schema.SName
import sttp.tapir.json.pickler.PicklerConfiguration

/** Everything one derivation needs to know beyond the type it derives for, passed explicitly through both halves.
  *
  * @param config
  *   the folded `PicklerConfiguration`
  * @param leafNameOverrides
  *   discriminator values that replace the configuration-derived ones for specific leaves (by `plainPrint`) — set by `oneOfUsingField`
  * @param implicitLookupExclusions
  *   types (by `plainPrint`) for which no user `Pickler` is looked up: the root (a `given p = Pickler.derived[A]` would find itself) and,
  *   for `oneOfUsingField`, the mapped leaves (whose codecs must be derived here, with the overridden tags)
  */
final case class DerivationEnv(
    config: PicklerConfiguration,
    leafNameOverrides: Map[String, String] = Map.empty,
    implicitLookupExclusions: Set[String] = Set.empty
)

/** Every name that ends up in the JSON or in the schema — field names, type names, discriminator values, enumeration values — computed
  * **once**, at expansion time, from the folded [[PicklerConfiguration]].
  *
  * Both halves splice the resulting `String`s as literals: the codec half into `JsonCodecMaker`'s configuration (which can only take
  * literals anyway), the schema half into the `Schema` constructors. There is therefore no second evaluation of the user's name
  * transformations at runtime, and no way for the two halves to disagree about a name.
  */
trait NameSupport { this: MacroCommons & AnnotationSupport & PlatformSupport =>

  /** The JSON name of a field: its `@encodedName` if present, otherwise `config.toEncodedName(scalaName)`. */
  protected def encodedFieldName[A: Type](
      param: Parameter,
      scalaName: String,
      config: PicklerConfiguration
  ): Either[PicklerDerivationError, String] =
    literalEncodedFieldName[A](param, scalaName) match {
      case Right(Some(explicit)) => Right(explicit)
      case Right(None)           => evaluating("toEncodedName", scalaName)(config.toEncodedName(scalaName))
      case Left(detail)          => Left(PicklerDerivationError.InvalidAnnotation(detail))
    }

  /** The configuration's functions are the user's code, evaluated by Hearth's interpreter; a body it cannot handle surfaces as an exception
    * here, which is turned into an actionable error rather than an `UndeclaredThrowableException`.
    */
  private def evaluating(function: String, input: String)(compute: => String): Either[PicklerDerivationError, String] =
    try Right(compute)
    catch { case scala.util.control.NonFatal(e) => Left(PicklerDerivationError.ConfigurationFunctionFailed(function, input, e)) }

  /** The `SName` of `A`: its type-level `@encodedName`, which replaces the name wholesale (type arguments included), or core's fully
    * qualified name plus the flattened, fully qualified type arguments.
    *
    * Only the type's *own* `@encodedName` is consulted: a parent's renaming is deliberately not propagated to its subtypes.
    */
  protected def sNameOf[A: Type]: SName =
    typeEncodedName[A] match {
      case Some(encoded) => SName(encoded, Nil)
      // The base name comes from core's own `SNameMacros`, which is the only way to get a properly qualified name for
      // a class nested in another class; the type arguments are flattened the way core's `Schema.renameWithTypeParameter`
      // does (`SName.typeParameterShortNames` is a misnomer: the entries are fully qualified).
      case None => SName(tapirFullName[A], flattenedTypeArguments[A])
    }

  /** The discriminator value written for, and documented on, leaf `A`: an `oneOfUsingField` override if there is one, otherwise
    * `config.toDiscriminatorValue(sNameOf[A])`.
    */
  protected def discriminatorValue[A: Type](env: DerivationEnv): Either[PicklerDerivationError, String] =
    env.leafNameOverrides.get(typeKey[A]) match {
      case Some(overridden) => Right(overridden)
      case None             =>
        val name = sNameOf[A]
        evaluating("toDiscriminatorValue", name.fullName)(env.config.toDiscriminatorValue(name))
    }

  /** `discriminatorValue` for every leaf, or the first failure. */
  protected def discriminatorValues[A](leaves: List[(String, ??<:[A])], env: DerivationEnv): Either[PicklerDerivationError, List[String]] =
    leaves.foldRight[Either[PicklerDerivationError, List[String]]](Right(Nil)) { case ((_, leaf), acc) =>
      import leaf.Underlying as Leaf
      for { tail <- acc; value <- discriminatorValue[Leaf](env) } yield value :: tail
    }

  /** The bare string an enumeration case `A` is written as. */
  protected def enumerationValue[A: Type]: String = enumCaseName[A](typeEncodedName[A])
}
