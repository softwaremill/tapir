package sttp.tapir.json.pickler.next.internal.compiletime

import scala.util.control.NoStackTrace

/** Errors raised during derivation.
  *
  * Modelling these as an ADT rather than passing strings around is what makes it possible to render one coherent, actionable message at the
  * end of a failed derivation (see REQ-10 in the plan) instead of a pile of unrelated compiler errors.
  *
  * Every error site follows the `Log.error(err.message) >> MIO.fail(err)` pattern, so that the failure shows up both in the derivation log
  * and as the compile error.
  */
sealed trait PicklerDerivationError extends NoStackTrace with Product with Serializable {
  def message: String
  override def getMessage: String = message
}

object PicklerDerivationError {

  /** No derivation rule was applicable. `reasons` carries one entry per rule that declined, so the user can see why each one bowed out.
    */
  final case class UnsupportedType(typeName: String, reasons: List[String]) extends PicklerDerivationError {
    def message: String =
      if (reasons.isEmpty) s"Cannot derive Pickler for $typeName"
      else s"Cannot derive Pickler for $typeName:\n${reasons.mkString("\n")}"
  }

  final case class NoChildrenInSealedTrait(typeName: String) extends PicklerDerivationError {
    def message: String =
      s"Cannot derive Pickler for $typeName: it is a sealed hierarchy with no children"
  }

  final case class CannotConstructType(typeName: String, detail: String) extends PicklerDerivationError {
    def message: String = s"Cannot construct a value of $typeName during decoding: $detail"
  }

  final case class UnexpectedParameterInSingleton(typeName: String, parameter: String) extends PicklerDerivationError {
    def message: String = s"Singleton $typeName unexpectedly has a constructor parameter '$parameter'"
  }

  /** The `PicklerConfiguration` could not be evaluated during expansion. The codec half needs the configuration as a *value* (its name
    * transformations are invoked at compile time and the results handed to `JsonCodecMaker` as literals), so a configuration that is only
    * known at runtime cannot be supported.
    */
  final case class ConfigurationNotStatic(configExpr: String, reason: String) extends PicklerDerivationError {
    def message: String =
      s"""The PicklerConfiguration must be known at compile time, but `$configExpr` could not be evaluated: $reason
         |Define it as a `given`/`implicit val` with a right-hand side built from `PicklerConfiguration.default` and its
         |`with*` methods (or an `inline given`), either in the same compilation unit as the derivation or in an already
         |compiled module. Functions passed to `withToEncodedName` must themselves be evaluable, e.g. `_.toUpperCase`.""".stripMargin
  }

  final case class NotASealedHierarchy(typeName: String, macroName: String) extends PicklerDerivationError {
    def message: String = s"$macroName can only be used with a sealed hierarchy or enum; $typeName is not one"
  }

  /** `derivedEnumeration` needs every case to be a singleton, because that is what a bare-string encoding can name. */
  final case class NotAnEnumeration(typeName: String, nonSingletons: List[String]) extends PicklerDerivationError {
    def message: String =
      s"""Pickler.derivedEnumeration can only be used with a sealed hierarchy or enum whose cases are all objects (or
         |parameterless enum cases); $typeName has cases with fields: ${nonSingletons.mkString(", ")}.
         |Use Pickler.derived[$typeName] or Pickler.oneOfUsingField instead.""".stripMargin
  }

  /** An all-singleton hierarchy is encoded as a bare string, which has no field to carry a discriminator. */
  final case class EnumerationInOneOfUsingField(typeName: String) extends PicklerDerivationError {
    def message: String =
      s"""Pickler.oneOfUsingField cannot be used with $typeName: all its cases are objects, so it is encoded as a bare
         |string with no field to hold the discriminator. Use Pickler.derivedEnumeration[$typeName].customStringBased(...)
         |to choose how each case is rendered.""".stripMargin
  }

  /** `oneOfUsingField` turns every mapping key into a jsoniter discriminator literal at compile time, so both the keys and `asString` have
    * to be evaluable during expansion.
    */
  final case class OneOfMappingNotStatic(detail: String) extends PicklerDerivationError {
    def message: String =
      s"""Pickler.oneOfUsingField needs its mapping keys and `asString` function to be known at compile time: $detail
         |Use literal keys (e.g. `200 -> picklerOk`) and a lambda over them (e.g. `code => s"code-$$code"`).""".stripMargin
  }

  /** A `JsonValueCodec[X]` alone cannot be honoured for a structural `X`: the schema would still be derived from the class, documenting a
    * shape the codec no longer writes.
    */
  final case class CodecWithoutPickler(typeName: String, codecExpr: String) extends PicklerDerivationError {
    def message: String =
      s"""Found a JsonValueCodec[$typeName] in scope ($codecExpr), but no Pickler[$typeName].
         |A codec on its own would be used for the JSON while the Schema is still derived from the class, so the two
         |could disagree. Provide a `given Pickler[$typeName]` instead (it carries both), or remove the codec.""".stripMargin
  }

  final case class InvalidAnnotation(detail: String) extends PicklerDerivationError {
    def message: String = s"Cannot derive Pickler: $detail"
  }

  final case class AssertionFailed(detail: String) extends PicklerDerivationError {
    def message: String = s"Assertion failed during Pickler derivation: $detail"
  }
}
