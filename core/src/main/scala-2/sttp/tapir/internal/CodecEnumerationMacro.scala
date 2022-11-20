package sttp.tapir.internal

import sttp.tapir.Codec.PlainCodec
import sttp.tapir.SchemaAnnotations
import sttp.tapir.macros.CreateDerivedEnumerationCodec

import scala.reflect.macros.blackbox

private[tapir] object CodecEnumerationMacro {
  def derivedEnumeration[L, T: c.WeakTypeTag](c: blackbox.Context): c.Expr[CreateDerivedEnumerationCodec[L, T]] = {
    import c.universe._

    val SchemaAnnotations = typeOf[SchemaAnnotations[_]]
    val weakTypeT = weakTypeOf[T]
    val schemaAnnotations = c.inferImplicitValue(appliedType(SchemaAnnotations, weakTypeT))

    // calling another macro - Validator.derivedEnumeration
    c.Expr[CreateDerivedEnumerationCodec[L, T]](q"""
      new _root_.sttp.tapir.macros.CreateDerivedEnumerationCodec(_root_.sttp.tapir.Validator.derivedEnumeration, $schemaAnnotations)
    """)
  }

  def derivedEnumerationValueCustomise[L: c.WeakTypeTag, T: c.WeakTypeTag](
      c: blackbox.Context
  ): c.Expr[CreateDerivedEnumerationCodec[L, T]] = {
    import c.universe._

    val SchemaAnnotations = typeOf[SchemaAnnotations[_]]
    val weakTypeT = weakTypeOf[T]
    val weakTypeL = weakTypeOf[L]
    val validator = ValidatorEnumerationMacro.enumerationValueValidator[T](c)
    val schemaAnnotations = c.inferImplicitValue(appliedType(SchemaAnnotations, weakTypeT))

    c.Expr[CreateDerivedEnumerationCodec[L, T]](q"""
      new _root_.sttp.tapir.macros.CreateDerivedEnumerationCodec[$weakTypeL, $weakTypeT]($validator, $schemaAnnotations)
    """)
  }

  def derivedEnumerationValue[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[PlainCodec[T]] = {
    import c.universe._
    c.Expr[PlainCodec[T]](q"""(${derivedEnumerationValueCustomise[String, T](c)}).defaultStringBased""")
  }
}
