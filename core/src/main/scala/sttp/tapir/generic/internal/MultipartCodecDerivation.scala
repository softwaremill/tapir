package sttp.tapir.generic.internal

import sttp.tapir.MultipartCodec
import sttp.tapir.generic.Configuration

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

trait MultipartCodecDerivation {
  implicit def multipartCaseClassCodec[T <: Product with Serializable](
      implicit conf: Configuration
  ): MultipartCodec[T] =
    macro MultipartCodecDerivation.generateForCaseClass[T]
}

object MultipartCodecDerivation {
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[MultipartCodec[T]] = {
    import c.universe._

    @tailrec
    def firstNotEmpty(candidates: List[() => (Tree, Tree)]): (Tree, Tree) = candidates match {
      case Nil => (EmptyTree, EmptyTree)
      case h :: t =>
        val (a, b) = h()
        val result = c.typecheck(b, silent = true)
        if (result == EmptyTree) firstNotEmpty(t) else (a, result)
    }

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c)
    val fields = util.fields

    def fieldIsPart(field: Symbol): Boolean = field.typeSignature.typeSymbol.fullName.startsWith("sttp.model.Part")
    def partTypeArg(field: Symbol): Type = field.typeSignature.typeArgs.head

    val fieldsWithCodecs = fields.map { field =>
      val codecType = if (fieldIsPart(field)) partTypeArg(field) else field.typeSignature

      val codecsToCheck = List(
        () =>
          (
            q"sttp.tapir.RawBodyType.StringBody(java.nio.charset.StandardCharsets.UTF_8)",
            q"implicitly[sttp.tapir.Codec[List[String], $codecType, sttp.tapir.CodecFormat.TextPlain]]"
          ),
        () =>
          (
            q"sttp.tapir.RawBodyType.StringBody(java.nio.charset.StandardCharsets.UTF_8)",
            q"implicitly[sttp.tapir.Codec[List[String], $codecType, _ <: sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"sttp.tapir.RawBodyType.ByteArrayBody",
            q"implicitly[sttp.tapir.Codec[List[Array[Byte]], $codecType, _ <: sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"sttp.tapir.RawBodyType.InputStreamBody",
            q"implicitly[sttp.tapir.Codec[List[java.io.InputStream], $codecType, _ <: sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"sttp.tapir.RawBodyType.ByteBufferBody",
            q"implicitly[sttp.tapir.Codec[List[java.nio.ByteBuffer], $codecType, _ <: sttp.tapir.CodecFormat]]"
          ),
        () =>
          (q"sttp.tapir.RawBodyType.FileBody", q"implicitly[sttp.tapir.Codec[List[java.io.File], $codecType, _ <: sttp.tapir.CodecFormat]]")
      )

      val codec = firstNotEmpty(codecsToCheck)
      if (codec._2 == EmptyTree) {
        c.abort(c.enclosingPosition, s"Cannot find a codec between a List[T] for some basic type T and: $codecType")
      }

      (field, codec)
    }

    val partCodecPairs = fieldsWithCodecs.map {
      case (field, (_, codec)) =>
        val fieldName = field.name.decodedName.toString
        q"""$conf.toLowLevelName($fieldName) -> $codec"""
    }

    val partCodecs = q"""Map(..$partCodecPairs)"""

    val partBodyTypesPairs = fieldsWithCodecs.map {
      case (field, (bodyType, _)) =>
        val fieldName = field.name.decodedName.toString
        q"""$conf.toLowLevelName($fieldName) -> $bodyType"""
    }

    val partBodyTypes = q"""Map(..$partBodyTypesPairs)"""

    val encodeParams: Iterable[Tree] = fields.map { field =>
      val fieldName = field.name.asInstanceOf[TermName]
      val fieldNameAsString = fieldName.decodedName.toString
      val transformedName = q"val transformedName = $conf.toLowLevelName($fieldNameAsString)"

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
        q"""val transformedName = $conf.toLowLevelName($fieldName)
            partsByName(transformedName)"""
      } else {
        q"""val transformedName = $conf.toLowLevelName($fieldName)
            partsByName(transformedName).body"""
      }
    }

    val codecTree = q"""
      {
        def decode(parts: Seq[sttp.tapir.RawPart]): $t = {
          val partsByName: Map[String, sttp.tapir.RawPart] = parts.map(p => p.name -> p).toMap
          val values = List(..$decodeParams)
          ${util.instanceFromValues}
        }
        def encode(o: $t): Seq[sttp.tapir.RawPart] = List(..$encodeParams)

        val bodyType = sttp.tapir.RawBodyType.MultipartBody($partBodyTypes, None)
        val codec = sttp.tapir.Codec.rawPartCodec($partCodecs, None)
          .map(decode _)(encode _)
          .schema(${util.schema})
          .validate(implicitly[sttp.tapir.Validator[$t]])
          
        (bodyType, codec)  
      }
     """
    Debug.logGeneratedCode(c)(t.typeSymbol.fullName, codecTree)

    c.Expr[MultipartCodec[T]](codecTree)
  }
}
