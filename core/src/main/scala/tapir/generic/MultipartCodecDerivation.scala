package tapir.generic

import tapir.{AnyPart, Codec, MediaType}

import scala.reflect.macros.blackbox

trait MultipartCodecDerivation {
  implicit def multipartCaseClassCodec[T <: Product with Serializable](
      implicit conf: Configuration
  ): Codec[T, MediaType.MultipartFormData, Seq[AnyPart]] =
    macro MultipartCodecDerivation.generateForCaseClass[T]
}

object MultipartCodecDerivation {
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[Codec[T, MediaType.MultipartFormData, Seq[AnyPart]]] = {

    import c.universe._

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c)
    val fields = util.fields

    def fieldIsPart(field: Symbol): Boolean = field.typeSignature.typeSymbol.fullName.startsWith("tapir.model.Part")
    def partTypeArg(field: Symbol): Type = field.typeSignature.typeArgs.head

    val fieldsWithCodecs = fields.map { field =>
      val codecType = if (fieldIsPart(field)) partTypeArg(field) else field.typeSignature

      val plainCodec = c.typecheck(q"implicitly[tapir.CodecForMany[$codecType, tapir.MediaType.TextPlain, _]]", silent = true)
      val codec = if (plainCodec == EmptyTree) {
        c.typecheck(q"implicitly[tapir.CodecForMany[$codecType, _ <: tapir.MediaType, _]]")
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
                       tapir.model.Part(transformedName, o.$fieldName)"""

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
        def decode(parts: Seq[tapir.AnyPart]): $t = {
          val partsByName: Map[String, tapir.AnyPart] = parts.map(p => p.name -> p).toMap
          val values = List(..$decodeParams)
          ${util.instanceFromValues}
        }
        def encode(o: $t): Seq[tapir.AnyPart] = List(..$encodeParams)

        tapir.Codec.multipartCodec($partCodecs, None)
          .map(decode _)(encode _)
          .schema(${util.schema}.schema)
      }
     """

    c.Expr[Codec[T, MediaType.MultipartFormData, Seq[AnyPart]]](codecTree)
  }
}
