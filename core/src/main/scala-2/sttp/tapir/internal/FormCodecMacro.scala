package sttp.tapir.internal

import sttp.tapir.generic.Configuration
import sttp.tapir.{Codec, CodecFormat, Schema}

import scala.reflect.macros.blackbox

private[tapir] object FormCodecMacro {
  // http://blog.echo.sh/2013/11/04/exploring-scala-macros-map-to-case-class-conversion.html
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[Codec[String, T, CodecFormat.XWwwFormUrlencoded]] = {
    import c.universe._

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c, "form codec")
    val fields = util.fields

    val fieldsWithCodecs = fields.map { field =>
      (
        field,
        c.typecheck(
          q"_root_.scala.Predef.implicitly[_root_.sttp.tapir.Codec[List[String], ${field.typeSignature}, _root_.sttp.tapir.CodecFormat.TextPlain]]"
        )
      )
    }

    val encodedNameType = c.weakTypeOf[Schema.annotations.encodedName]
    val encodeParams: Iterable[Tree] = fieldsWithCodecs.map { case (field, codec) =>
      val fieldName = field.name.asInstanceOf[TermName]
      val fieldNameAsString = fieldName.decodedName.toString
      val encodedName = util.extractStringArgFromAnnotation(field, encodedNameType)
      q"""val transformedName = $encodedName.getOrElse($conf.toEncodedName($fieldNameAsString))
          $codec.encode(o.$fieldName).map(v => (transformedName, v))"""
    }

    val decodeParams = fieldsWithCodecs.map { case (field, codec) =>
      val fieldName = field.name.decodedName.toString
      val encodedName = util.extractStringArgFromAnnotation(field, encodedNameType)
      q"""val transformedName = $encodedName.getOrElse($conf.toEncodedName($fieldName))
          $codec.decode(paramsMap.get(transformedName).toList.flatten)"""
    }

    val codecTree = q"""
      {
        def decode(params: Seq[(String, String)]): sttp.tapir.DecodeResult[$t] = {
          val paramsMap: Map[String, Seq[String]] = params.groupBy(_._1).transform((_, v) => v.map(_._2))
          val decodeResults = List(..$decodeParams)
          _root_.sttp.tapir.DecodeResult.sequence(decodeResults).map { values =>
            ${util.instanceFromValues}
          }
        }
        def encode(o: $t): _root_.scala.Seq[(_root_.java.lang.String, _root_.java.lang.String)] = _root_.scala.List(..$encodeParams).flatten

        _root_.sttp.tapir.Codec.formSeqUtf8
          .mapDecode(decode _)(encode _)
          .schema(${util.schema})
      }
     """
    Debug.logGeneratedCode(c)(t.typeSymbol.fullName, codecTree)
    c.Expr[Codec[String, T, CodecFormat.XWwwFormUrlencoded]](codecTree)
  }
}
