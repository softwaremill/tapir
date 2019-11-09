package sttp.tapir.generic.internal

import sttp.tapir.generic.Configuration
import sttp.tapir.{AnyPart, Codec, CodecFormat}

import scala.reflect.macros.blackbox

trait MultipartCodecDerivation {
  implicit def multipartCaseClassCodec[T <: Product with Serializable](
      implicit conf: Configuration
  ): Codec[T, CodecFormat.MultipartFormData, Seq[AnyPart]] =
    macro MultipartCodecDerivation.generateForCaseClass[T]
}

object MultipartCodecDerivation {
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[Codec[T, CodecFormat.MultipartFormData, Seq[AnyPart]]] = {
    import c.universe._

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c)
    val fields = util.fields

    def fieldIsPart(field: Symbol): Boolean = field.typeSignature.typeSymbol.fullName.startsWith("sttp.model.Part")
    def partTypeArg(field: Symbol): Type = field.typeSignature.typeArgs.head

    val fieldsWithCodecs = fields.map { field =>
      val codecType = if (fieldIsPart(field)) partTypeArg(field) else field.typeSignature

      val plainCodec = c.typecheck(q"implicitly[sttp.tapir.CodecForMany[$codecType, sttp.tapir.CodecFormat.TextPlain, _]]", silent = true)
      val codec = if (plainCodec == EmptyTree) {
        c.typecheck(q"implicitly[sttp.tapir.CodecForMany[$codecType, _ <: sttp.tapir.CodecFormat, _]]")
      } else plainCodec

      (field, codec)
    }

    val partCodecPairs = fieldsWithCodecs.map {
      case (field, codec) =>
        val fieldName = field.name.decodedName.toString
        q"""$conf.transformMemberName($fieldName) -> $codec"""
    }

    val partCodecs = q"""Map(..$partCodecPairs)"""

    val encodeParams: Iterable[Tree] = fields.map { field =>
      val fieldName = field.name.asInstanceOf[TermName]
      val fieldNameAsString = fieldName.decodedName.toString
      val transformedName = q"val transformedName = $conf.transformMemberName($fieldNameAsString)"

      if (fieldIsPart(field)) {
        q"""$transformedName
            o.$fieldName.copy(name = transformedName)"""
      } else {
        val base = q"""$transformedName
                       sttp.model.Part(transformedName, o.$fieldName)"""

        // if the field is a File/Path, and is not wrapped in a Part, during encoding adding the file's name
        val fieldTypeName = field.typeSignature.typeSymbol.fullName
        if (fieldTypeName.startsWith("java.io.File")) {
          q"$base.fileName(o.$fieldName.getName)"
        } else if (fieldTypeName.startsWith("java.io.File")) {
          q"$base.fileName(o.$fieldName.toFile.getName)"
        } else {
          base
        }
      }
    }

    val decodeParams = fields.map { field =>
      val fieldName = field.name.decodedName.toString
      if (fieldIsPart(field)) {
        q"""val transformedName = $conf.transformMemberName($fieldName)
            partsByName(transformedName)"""
      } else {
        q"""val transformedName = $conf.transformMemberName($fieldName)
            partsByName(transformedName).body"""
      }
    }

    val codecTree = q"""
      {
        def decode(parts: Seq[sttp.tapir.AnyPart]): $t = {
          val partsByName: Map[String, sttp.tapir.AnyPart] = parts.map(p => p.name -> p).toMap
          val values = List(..$decodeParams)
          ${util.instanceFromValues}
        }
        def encode(o: $t): Seq[sttp.tapir.AnyPart] = List(..$encodeParams)

        sttp.tapir.Codec.multipartCodec($partCodecs, None)
          .map(decode _)(encode _)
          .schema(${util.schema})
          .validate(implicitly[sttp.tapir.Validator[$t]])
      }
     """
    Debug.logGeneratedCode(c)(t.typeSymbol.fullName, codecTree)
    c.Expr[Codec[T, CodecFormat.MultipartFormData, Seq[AnyPart]]](codecTree)
  }
}
