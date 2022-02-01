package sttp.tapir.internal

import sttp.tapir.Schema.SchemaAnnotations

import scala.reflect.macros.blackbox

object SchemaAnnotationsMacro {
  def derived[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[SchemaAnnotations[T]] = {
    import c.universe._

    val ArrayObj = reify(Array).tree
    val JavaAnnotationTpe = typeOf[java.lang.annotation.Annotation]

    val annotations = weakTypeOf[T].typeSymbol.annotations.collect {
      case annotation if !(annotation.tree.tpe <:< JavaAnnotationTpe) =>
        annotation.tree
    }

    c.Expr[SchemaAnnotations[T]](
      q"""_root_.sttp.tapir.Schema.SchemaAnnotations.apply($ArrayObj(..$annotations): Array[_root_.scala.Any])"""
    )
  }
}
