package sttp.tapir.json.pickler.next.internal.compiletime

import scala.util.control.NoStackTrace

/** Errors raised during derivation.
  *
  * Modelling these as an ADT rather than passing strings around is what makes it possible to render one coherent,
  * actionable message at the end of a failed derivation (see REQ-10 in the plan) instead of a pile of unrelated
  * compiler errors.
  *
  * Every error site follows the `Log.error(err.message) >> MIO.fail(err)` pattern, so that the failure shows up both in
  * the derivation log and as the compile error.
  */
sealed trait PicklerDerivationError extends NoStackTrace with Product with Serializable {
  def message: String
}

object PicklerDerivationError {

  /** No derivation rule was applicable. `reasons` carries one entry per rule that declined, so the user can see why
    * each one bowed out.
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

  final case class UnexpectedParameterInSingleton(typeName: String, parameter: String)
      extends PicklerDerivationError {
    def message: String = s"Singleton $typeName unexpectedly has a constructor parameter '$parameter'"
  }

  /** The `PicklerConfiguration` could not be evaluated during expansion. The codec half needs the configuration as a
    * *value* (its name transformations are invoked at compile time and the results handed to `JsonCodecMaker` as
    * literals), so a configuration that is only known at runtime cannot be supported.
    */
  final case class ConfigurationNotStatic(configExpr: String, reason: String) extends PicklerDerivationError {
    def message: String =
      s"""The PicklerConfiguration must be known at compile time, but `$configExpr` could not be evaluated: $reason
         |Define it as a `given`/`implicit val` with a right-hand side built from `PicklerConfiguration.default` and its
         |`with*` methods (or an `inline given`), either in the same compilation unit as the derivation or in an already
         |compiled module. Functions passed to `withToEncodedName` must themselves be evaluable, e.g. `_.toUpperCase`.""".stripMargin
  }

  final case class InvalidAnnotation(detail: String) extends PicklerDerivationError {
    def message: String = s"Cannot derive Pickler: $detail"
  }

  final case class AssertionFailed(detail: String) extends PicklerDerivationError {
    def message: String = s"Assertion failed during Pickler derivation: $detail"
  }
}
