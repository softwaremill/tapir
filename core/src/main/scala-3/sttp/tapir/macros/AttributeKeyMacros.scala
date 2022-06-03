package sttp.tapir.macros

import sttp.tapir.AttributeKey

import scala.quoted.*

trait AttributeKeyMacros {
  inline def apply[T]: AttributeKey[T] = ${ AttributeKeyMacros[T] }
}

private[tapir] object AttributeKeyMacros {
  def apply[T: Type](using q: Quotes): Expr[AttributeKey[T]] = {
    import quotes.reflect.*
    val t = TypeRepr.of[T]

    '{ new AttributeKey[T](${ Expr(t.show) }) }
  }
}
