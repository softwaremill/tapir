package sttp.tapir.internal

import sttp.tapir.Schema
import sttp.tapir.macros.CreateDerivedEnumerationSchema

import scala.reflect.macros.blackbox

object SchemaEnumerationMacro {
  def derivedEnumeration[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[CreateDerivedEnumerationSchema[T]] = {
    import c.universe._

    // this needs to be a macro so that we can call another macro - Validator.derivedEnumeration
    c.Expr[CreateDerivedEnumerationSchema[T]](q"""
      new sttp.tapir.macros.CreateDerivedEnumerationSchema(Validator.derivedEnumeration)
    """)
  }

  def derivedEnumerationValue[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Schema[T]] = {
    import c.universe._

    val Enumeration = typeOf[scala.Enumeration]
    val SchemaAnnotations = typeOf[sttp.tapir.internal.SchemaAnnotations[_]]

    val weakTypeT = weakTypeOf[T]
    val owner = weakTypeT.typeSymbol.owner

    if (!(owner.asClass.toType <:< Enumeration)) {
      c.abort(c.enclosingPosition, "Can only derive Schema for values owned by scala.Enumeration")
    } else {

      val enumeration = TermName(weakTypeT.toString.split("\\.").dropRight(1).last)
      val validator = q"sttp.tapir.Validator.enumeration($enumeration.values.toList)"
      val schemaAnnotations = c.inferImplicitValue(appliedType(SchemaAnnotations, weakTypeT))

      c.Expr[Schema[T]](q"$schemaAnnotations.enrich(Schema.string[$weakTypeT].validate($validator))")
    }
  }
}
