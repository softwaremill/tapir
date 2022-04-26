package sttp.tapir.internal

import sttp.tapir.macros.CreateDerivedEnumerationCodec

import scala.reflect.macros.blackbox

private[tapir] object CodecEnumerationMacro {
  def derivedEnumeration[L, T: c.WeakTypeTag](c: blackbox.Context): c.Expr[CreateDerivedEnumerationCodec[L, T]] = {
    import c.universe._

    // this needs to be a macro so that we can call another macro - Validator.derivedEnumeration
    c.Expr[CreateDerivedEnumerationCodec[L, T]](q"""
      new sttp.tapir.macros.CreateDerivedEnumerationCodec(Validator.derivedEnumeration)
    """)
  }
}
