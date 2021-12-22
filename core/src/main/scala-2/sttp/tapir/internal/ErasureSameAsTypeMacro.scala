package sttp.tapir.internal

import sttp.tapir.typelevel.ErasureSameAsType

import scala.reflect.macros.blackbox

object ErasureSameAsTypeMacro {
  def instance[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ErasureSameAsType[T]] = {
    import c.universe._
    mustBeEqualToItsErasure[T](c)
    c.Expr[ErasureSameAsType[T]](q"new _root_.sttp.tapir.typelevel.ErasureSameAsType[${implicitly[c.WeakTypeTag[T]]}] {}")
  }

  private def mustBeEqualToItsErasure[T: c.WeakTypeTag](c: blackbox.Context): Unit = {
    import c.universe._

    val t = implicitly[c.WeakTypeTag[T]].tpe.dealias

    if (!(t =:= t.erasure) && !(t =:= typeOf[Unit])) {
      c.error(
        c.enclosingPosition,
        s"Type $t is not the same as its erasure. Using a runtime-class-based check it won't be possible to verify " +
          s"that the input matches the desired type. Use other methods to match the input to the appropriate variant " +
          s"instead."
      )
    }
  }
}
