package sttp.tapir.internal

import sttp.tapir.{Schema, SchemaAnnotations}
import sttp.tapir.macros.CreateDerivedEnumerationSchema

import scala.reflect.macros.blackbox

private[tapir] object SchemaEnumerationMacro {
  def derivedEnumeration[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[CreateDerivedEnumerationSchema[T]] = {
    import c.universe._

    val SchemaAnnotations = typeOf[SchemaAnnotations[_]]
    val weakTypeT = weakTypeOf[T]
    val schemaAnnotations = c.inferImplicitValue(appliedType(SchemaAnnotations, weakTypeT))

    // calling another macro - Validator.derivedEnumeration
    c.Expr[CreateDerivedEnumerationSchema[T]](q"""
      new _root_.sttp.tapir.macros.CreateDerivedEnumerationSchema(_root_.sttp.tapir.Validator.derivedEnumeration, $schemaAnnotations)
    """)
  }

  def derivedEnumerationValueCustomise[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[CreateDerivedEnumerationSchema[T]] = {
    import c.universe._

    val SchemaAnnotations = typeOf[SchemaAnnotations[_]]
    val weakTypeT = weakTypeOf[T]
    val validator = ValidatorEnumerationMacro.enumerationValueValidator[T](c)
    val schemaAnnotations = c.inferImplicitValue(appliedType(SchemaAnnotations, weakTypeT))

    c.Expr[CreateDerivedEnumerationSchema[T]](q"""
      new _root_.sttp.tapir.macros.CreateDerivedEnumerationSchema[$weakTypeT]($validator, $schemaAnnotations)
    """)
  }

  def derivedEnumerationValue[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Schema[T]] = {
    import c.universe._
    c.Expr[Schema[T]](q"""(${derivedEnumerationValueCustomise[T](c)}).defaultStringBased""")
  }
}
