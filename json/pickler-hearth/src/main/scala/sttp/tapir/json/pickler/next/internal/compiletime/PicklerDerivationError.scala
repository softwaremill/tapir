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

  final case class AssertionFailed(detail: String) extends PicklerDerivationError {
    def message: String = s"Assertion failed during Pickler derivation: $detail"
  }
}
