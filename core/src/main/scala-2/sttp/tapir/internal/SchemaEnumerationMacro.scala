package sttp.tapir.internal

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
}
