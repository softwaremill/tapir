package sttp.tapir.internal

import sttp.tapir.Validator

import scala.reflect.macros.blackbox

private[tapir] object ValidatorEnumerationMacro {
  // based on: https://stackoverflow.com/questions/13671734/iteration-over-a-sealed-trait-in-scala
  def apply[E: c.WeakTypeTag](c: blackbox.Context): c.Expr[Validator.Enumeration[E]] = {
    import c.universe._

    val t = weakTypeOf[E]
    val symbol = t.typeSymbol.asClass
    if (!symbol.isClass || !symbol.isSealed) {
      c.abort(c.enclosingPosition, "Can only enumerate values of a sealed trait or class.")
    } else {

      def flatChildren(s: ClassSymbol): List[ClassSymbol] = s.knownDirectSubclasses.toList.map(_.asClass).flatMap { child =>
        if (child.isModuleClass)
          List(child)
        else if (child.isSealed)
          flatChildren(child)
        else
          c.abort(c.enclosingPosition, "All children must be objects or sealed parent of such.")
      }
      val subclasses = flatChildren(symbol).sortBy(_.name.encodedName.toString).distinct
      if (!subclasses.forall(_.isModuleClass)) {
        c.abort(c.enclosingPosition, "All children must be objects.")
      } else {
        val instances = subclasses.map(x => Ident(x.asInstanceOf[scala.reflect.internal.Symbols#Symbol].sourceModule.asInstanceOf[Symbol]))
        val validatorEnum =
          q"_root_.sttp.tapir.Validator.Enumeration($instances, _root_.scala.None, _root_.scala.Some(_root_.sttp.tapir.Schema.SName(${symbol.fullName})))"
        Debug.logGeneratedCode(c)(t.typeSymbol.fullName, validatorEnum)
        c.Expr[Validator.Enumeration[E]](validatorEnum)
      }
    }
  }

  // for Scala's Enumerations
  def enumerationValueValidator[T: c.WeakTypeTag](c: blackbox.Context): c.universe.Tree = {
    import c.universe._

    val Enumeration = typeOf[scala.Enumeration]

    val weakTypeT = weakTypeOf[T]
    val owner = weakTypeT.typeSymbol.owner

    if (!(owner.asClass.toType <:< Enumeration)) {
      c.abort(c.enclosingPosition, "Can only derive Schema for values owned by scala.Enumeration")
    } else {
      val enumNameComponents = weakTypeT.toString.split("\\.").dropRight(1)
      val enumeration = enumNameComponents.toList match {
        case head :: tail => tail.foldLeft[Tree](Ident(TermName(head))) { case (tree, nextName) => Select(tree, TermName(nextName)) }
        case Nil          => c.abort(c.enclosingPosition, s"Invalid enum name: ${weakTypeT.toString}")
      }

      q"_root_.sttp.tapir.Validator.enumeration($enumeration.values.toList, v => _root_.scala.Option(v), _root_.scala.Some(sttp.tapir.Schema.SName(${enumNameComponents
          .mkString(".")})))"
    }
  }
}
