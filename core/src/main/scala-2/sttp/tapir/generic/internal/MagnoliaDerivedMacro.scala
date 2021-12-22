package sttp.tapir.generic.internal

import magnolia1.Magnolia
import sttp.tapir.generic.Derived

object MagnoliaDerivedMacro {
  import scala.reflect.macros.whitebox

  def generateDerivedGen[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Derived[T]] = {
    import c.universe._
    c.Expr[Derived[T]](q"_root_.sttp.tapir.generic.Derived(${Magnolia.gen[T](c)(implicitly[c.WeakTypeTag[T]])})")
  }
}
