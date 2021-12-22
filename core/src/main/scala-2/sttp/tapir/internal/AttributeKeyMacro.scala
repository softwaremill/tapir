package sttp.tapir.internal

import sttp.tapir.AttributeKey

import scala.reflect.macros.blackbox

object AttributeKeyMacro {
  def apply[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AttributeKey[T]] = {
    import c.universe._
    c.Expr[AttributeKey[T]](
      q"new _root_.sttp.tapir.AttributeKey(${c.universe.show(implicitly[c.WeakTypeTag[T]].tpe)})"
    )
  }
}
