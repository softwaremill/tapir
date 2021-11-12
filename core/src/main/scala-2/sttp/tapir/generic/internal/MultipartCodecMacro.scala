package sttp.tapir.generic.internal

import sttp.tapir.{MultipartCodec, Schema}
import sttp.tapir.generic.Configuration
import sttp.tapir.internal.CaseClassUtil

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object MultipartCodecMacro {
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[MultipartCodec[T]] = {
    import c.universe._

    @tailrec
    def firstNotEmpty(candidates: List[() => (Tree, Tree)]): (Tree, Tree) =
      candidates match {
        case Nil => (EmptyTree, EmptyTree)
        case h :: t =>
          val (a, b) = h()
          val result = c.typecheck(b, silent = true)
          if (result == EmptyTree) firstNotEmpty(t) else (a, result)
      }

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c, "multipart code")
    val fields = util.fields

    def fieldIsPart(field: Symbol): Boolean = field.typeSignature.typeSymbol.fullName.startsWith("sttp.model.Part")
    def partTypeArg(field: Symbol): Type = field.typeSignature.typeArgs.head

    val fieldsWithCodecs = fields.map { field =>
      val codecType = if (fieldIsPart(field)) partTypeArg(field) else field.typeSignature

      val codecsToCheck = List(
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.StringBody(_root_.java.nio.charset.StandardCharsets.UTF_8)",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.java.lang.String], $codecType, _root_.sttp.tapir.CodecFormat.TextPlain]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.StringBody(_root_.java.nio.charset.StandardCharsets.UTF_8)",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.java.lang.String], $codecType, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.ByteArrayBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.scala.Array[_root_.scala.Byte]], $codecType, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.InputStreamBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.java.io.InputStream], $codecType, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.ByteBufferBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.java.nio.ByteBuffer], $codecType, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.FileBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.sttp.tapir.FileRange], $codecType, _ <: _root_.sttp.tapir.CodecFormat]]"
          )
      )

      val codec = firstNotEmpty(codecsToCheck)
      if (codec._2 == EmptyTree) {
        c.abort(c.enclosingPosition, s"Cannot find a codec between a List[T] for some basic type T and: $codecType")
      }

      (field, codec)
    }

    val encodedNameType = c.weakTypeOf[Schema.annotations.encodedName]
    val partCodecPairs = fieldsWithCodecs.map { case (field, (bodyType, codec)) =>
      val fieldName = field.name.decodedName.toString
      val encodedName = util.extractStringArgFromAnnotation(field, encodedNameType)
      q"""($encodedName.getOrElse($conf.toEncodedName($fieldName)), _root_.sttp.tapir.PartCodec($bodyType, $codec))"""
    }

    val partCodecs = q"""_root_.scala.collection.immutable.Map(..$partCodecPairs)"""

    val encodeParams: Iterable[Tree] = fields.map { field =>
      val fieldName = field.name.asInstanceOf[TermName]
      val fieldNameAsString = fieldName.decodedName.toString
      val encodedName = util.extractStringArgFromAnnotation(field, encodedNameType)
      val transformedName = q"val transformedName = $encodedName.getOrElse($conf.toEncodedName($fieldNameAsString))"

      if (fieldIsPart(field)) {
        q"""$transformedName
            o.$fieldName.copy(name = transformedName)"""
      } else {
        val base = q"""$transformedName
                       _root_.sttp.model.Part(transformedName, o.$fieldName)"""

        // if the field is a File/Path, and is not wrapped in a Part, during encoding adding the file's name
        val fieldTypeName = field.typeSignature.typeSymbol.fullName
        if (fieldTypeName.startsWith("java.io.File")) {
          q"$base.fileName(o.$fieldName.getName)"
        } else if (fieldTypeName.startsWith("java.nio.Path")) {
          q"$base.fileName(o.$fieldName.toFile.getName)"
        } else if (fieldTypeName.startsWith("org.scalajs.dom.File")) {
          q"$base.fileName(o.$fieldName.name)"
        } else {
          base
        }
      }
    }

    val decodeParams = fields.map { field =>
      val fieldName = field.name.decodedName.toString
      val encodedName = util.extractStringArgFromAnnotation(field, encodedNameType)
      if (fieldIsPart(field)) {
        q"""val transformedName = $encodedName.getOrElse($conf.toEncodedName($fieldName))
            partsByName(transformedName)"""
      } else {
        q"""val transformedName = $encodedName.getOrElse($conf.toEncodedName($fieldName))
            partsByName(transformedName).body"""
      }
    }

    val codecTree = q"""
      {
        def decode(parts: _root_.scala.Seq[_root_.sttp.tapir.RawPart]): $t = {
          val partsByName: _root_.scala.collection.immutable.Map[_root_.java.lang.String, _root_.sttp.tapir.RawPart] = parts.map(p => p.name -> p).toMap
          val values = _root_.scala.List(..$decodeParams)
          ${util.instanceFromValues}
        }
        def encode(o: $t): _root_.scala.Seq[_root_.sttp.tapir.RawPart] = _root_.scala.List(..$encodeParams)

        _root_.sttp.tapir.Codec.multipart($partCodecs, _root_.scala.None)
          .map(decode _)(encode _)
          .schema(${util.schema})
      }
     """
    Debug.logGeneratedCode(c)(t.typeSymbol.fullName, codecTree)

    c.Expr[MultipartCodec[T]](codecTree)
  }
}
