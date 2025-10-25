package sttp.tapir.internal

import sttp.tapir.generic.Configuration
import sttp.tapir.{MultipartCodec, Schema}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

private[tapir] object MultipartCodecMacro {
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[MultipartCodec[T]] = {
    import c.universe._

    @tailrec
    def firstNotEmpty(candidates: List[() => (Tree, Tree)]): (Tree, Tree) =
      candidates match {
        case Nil    => (EmptyTree, EmptyTree)
        case h :: t =>
          val (a, b) = h()
          val result = c.typecheck(b, silent = true)
          if (result == EmptyTree) firstNotEmpty(t) else (a, result)
      }

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c, "multipart code")
    val fields = util.fields

    val fieldsWithCodecs = fields.map { field =>
      val codecsToCheck = List(
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.StringBody(_root_.java.nio.charset.StandardCharsets.UTF_8)",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.sttp.model.Part[_root_.java.lang.String]], $field, _root_.sttp.tapir.CodecFormat.TextPlain]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.StringBody(_root_.java.nio.charset.StandardCharsets.UTF_8)",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.sttp.model.Part[_root_.java.lang.String]], $field, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.ByteArrayBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.sttp.model.Part[_root_.scala.Array[_root_.scala.Byte]]], $field, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.InputStreamBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.sttp.model.Part[_root_.java.io.InputStream]], $field, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.ByteBufferBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.sttp.model.Part[_root_.java.nio.ByteBuffer]], $field, _ <: _root_.sttp.tapir.CodecFormat]]"
          ),
        () =>
          (
            q"_root_.sttp.tapir.RawBodyType.FileBody",
            q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[_root_.scala.List[_root_.sttp.model.Part[_root_.sttp.tapir.FileRange]], $field, _ <: _root_.sttp.tapir.CodecFormat]]"
          )
      )

      val codec = firstNotEmpty(codecsToCheck)
      if (codec._2 == EmptyTree) {
        c.abort(c.enclosingPosition, s"Cannot find a codec between a List[Part[T]] for some basic type T and: $field")
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

    val encodeParams: List[Tree] = fields.map { field =>
      val fieldName = field.name.asInstanceOf[TermName]
      val fieldNameAsString = fieldName.decodedName.toString
      val encodedName = util.extractStringArgFromAnnotation(field, encodedNameType)
      val transformedName = q"val transformedName = $encodedName.getOrElse($conf.toEncodedName($fieldNameAsString))"

      q"""$transformedName
          transformedName -> o.$fieldName"""
    }

    val decodeParams = fields.map { field =>
      val fieldName = field.name.decodedName.toString
      val encodedName = util.extractStringArgFromAnnotation(field, encodedNameType)

      q"""val transformedName = $encodedName.getOrElse($conf.toEncodedName($fieldName))
          partsByName(transformedName)"""
    }

    val codecTree = q"""
      {
        def decode(partsByName: _root_.scala.collection.immutable.ListMap[_root_.java.lang.String, Any]): $t = {
          val values = _root_.scala.List(..$decodeParams)
          ${util.instanceFromValues}
        }
        def encode(o: $t): _root_.scala.collection.immutable.ListMap[_root_.java.lang.String, Any] = _root_.scala.collection.immutable.ListMap(..$encodeParams)

        _root_.sttp.tapir.Codec.multipart($partCodecs, _root_.scala.None)
          .map(decode _)(encode _)
          .schema(${util.schema})
      }
     """

    Debug.logGeneratedCode(c)(t.typeSymbol.fullName, codecTree)

    c.Expr[MultipartCodec[T]](codecTree)
  }
}
