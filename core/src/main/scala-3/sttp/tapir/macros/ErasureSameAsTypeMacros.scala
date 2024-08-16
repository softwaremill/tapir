package sttp.tapir.macros

import sttp.tapir.typelevel.ErasureSameAsType

import scala.quoted.*

trait ErasureSameAsTypeMacros {
  implicit inline def instance[T]: ErasureSameAsType[T] = ${ ErasureSameAsTypeMacros.instanceImpl[T] }
}

private[tapir] object ErasureSameAsTypeMacros {
  def instanceImpl[T: Type](using Quotes): Expr[ErasureSameAsType[T]] = {
    mustBeEqualToItsErasure[T]
    '{ new ErasureSameAsType[T] {} }
  }

  private def mustBeEqualToItsErasure[T: Type](using Quotes): Unit = {
    import quotes.reflect._

    val t = TypeRepr.of[T]

    if t.<:<(TypeRepr.of[reflect.Enum]) && t.typeSymbol.declaredFields.isEmpty then
      report.errorAndAbort(
        s"Type ${t.show} is a parameterless enum case. Such cases do not have a distinct run-time class, and hence cannot" +
          s"be correctly used in a oneOfVariant. Consider using other matchers, such as oneOfVariantExactMatcher."
      )

    // substitute for `t =:= t.erasure` - https://github.com/lampepfl/dotty-feature-requests/issues/209
    val isAllowed: TypeRepr => Boolean = {
      case AppliedType(t, _) if t.typeSymbol.name == "Array" => true
      case _: AppliedType | _: AndOrType                     => false
      case _                                                 => true
    }

    if !isAllowed(t) then
      report.errorAndAbort(
        s"Type ${t.show}, $t is not the same as its erasure. Using a runtime-class-based check it won't be possible to verify " +
          s"that the input matches the desired type. Use other methods to match the input to the appropriate variant " +
          s"instead."
      )
  }
}
