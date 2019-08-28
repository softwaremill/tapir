package tapir.generic

import tapir.Validator

import scala.reflect.macros.blackbox

// based on: https://stackoverflow.com/questions/13671734/iteration-over-a-sealed-trait-in-scala
trait ValidatorEnumMacro {

  def validatorForEnum[E: c.WeakTypeTag](c: blackbox.Context): c.Expr[Validator.Primitive[E]] = {
    import c.universe._
    val symbol = weakTypeOf[E].typeSymbol.asClass
    if (!symbol.isClass || !symbol.isSealed) {
      c.abort(c.enclosingPosition, "Can only enumerate values of a sealed trait or class.")
    } else {
      val subclasses = symbol.knownDirectSubclasses.toList
      if (!subclasses.forall(_.isModuleClass)) {
        c.abort(c.enclosingPosition, "All children must be objects.")
      } else {
        val instances = subclasses.map(x => Ident(x.asInstanceOf[scala.reflect.internal.Symbols#Symbol].sourceModule.asInstanceOf[Symbol]))
        c.Expr[Validator.Primitive[E]](q"tapir.Validator.Enum($instances)")
      }
    }
  }
}
