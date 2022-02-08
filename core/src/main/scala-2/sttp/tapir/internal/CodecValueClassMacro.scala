package sttp.tapir.internal

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain

import scala.reflect.macros.blackbox

object CodecValueClassMacro {

  def derivedValueClass[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Codec[String, T, TextPlain]] = {
    import c.universe._

    val util = new CaseClassUtil[c.type, T](c, "value class codec")

    if (!util.isValueClass) {
      c.abort(c.enclosingPosition, s"Can only derive codec for value class.")
    } else {
      val field = util.fields.head
      val baseCodec = c.typecheck(
        q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[String, ${field.typeSignature}, _root_.sttp.tapir.CodecFormat.TextPlain]]"
      )
      val fieldName = field.name.asInstanceOf[TermName]
      val tree = q"$baseCodec.map(${util.companion}.apply(_))(_.$fieldName)"
      c.Expr[Codec[String, T, TextPlain]](tree)
    }
  }

}
