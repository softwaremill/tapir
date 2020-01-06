package sttp.tapir.generic.internal

import magnolia.Magnolia
import sttp.tapir.generic.Derived

object MagnoliaDerivedMacro {
  import scala.reflect.macros.whitebox

  def derivedGen[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Derived[T]] = {
    import c.universe._
    c.Expr[Derived[T]](q"sttp.tapir.generic.Derived(${Magnolia.gen[T](c)(implicitly[c.WeakTypeTag[T]])})")
  }
}
