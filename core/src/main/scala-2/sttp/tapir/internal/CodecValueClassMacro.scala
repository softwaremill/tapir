package sttp.tapir.internal

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain

import scala.reflect.macros.blackbox

object CodecValueClassMacro {

  def derivedValueClass[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Codec[String, T, TextPlain]] = {
    import c.universe._

    val tpe: Type = weakTypeOf[T]

    val isValueClass: Boolean = {
      import definitions._
      tpe.typeSymbol.isClass && tpe <:< AnyValTpe && !ScalaPrimitiveValueClasses.contains(tpe.typeSymbol.asClass)
    }

    if (!isValueClass) {
      c.abort(c.enclosingPosition, "Can only derive codec for value class.")
    } else {
      val constructor = tpe.decls.collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m }
      val fields = constructor.get.paramLists.head

      val field = fields.head
      val baseCodec = c.typecheck(
        q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[String, ${field.typeSignature}, _root_.sttp.tapir.CodecFormat.TextPlain]]"
      )
      val fieldName = field.name.asInstanceOf[TermName]
      val tree = q"$baseCodec.map(v => new ${Ident(tpe.typeSymbol.name.decodedName)}(v))(_.$fieldName)"
      c.Expr[Codec[String, T, TextPlain]](tree)
    }
  }

}
