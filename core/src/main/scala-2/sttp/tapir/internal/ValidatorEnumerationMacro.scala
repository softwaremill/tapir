package sttp.tapir.internal

import sttp.tapir.Validator

import scala.reflect.macros.blackbox

// based on: https://stackoverflow.com/questions/13671734/iteration-over-a-sealed-trait-in-scala
private[tapir] object ValidatorEnumerationMacro {
  def apply[E: c.WeakTypeTag](c: blackbox.Context): c.Expr[Validator.Enumeration[E]] = {
    import c.universe._

    val t = weakTypeOf[E]
    val symbol = t.typeSymbol.asClass
    if (!symbol.isClass || !symbol.isSealed) {
      c.abort(c.enclosingPosition, "Can only enumerate values of a sealed trait or class.")
    } else {
      val subclasses = symbol.knownDirectSubclasses.toList.sortBy(_.name.encodedName.toString)
      if (!subclasses.forall(_.isModuleClass)) {
        c.abort(c.enclosingPosition, "All children must be objects.")
      } else {
        val instances = subclasses.map(x => Ident(x.asInstanceOf[scala.reflect.internal.Symbols#Symbol].sourceModule.asInstanceOf[Symbol]))
        val validatorEnum =
          q"_root_.sttp.tapir.Validator.Enumeration($instances, None, Some(_root_.sttp.tapir.Schema.SName(${symbol.fullName})))"
        Debug.logGeneratedCode(c)(t.typeSymbol.fullName, validatorEnum)
        c.Expr[Validator.Enumeration[E]](validatorEnum)
      }
    }
  }
}
