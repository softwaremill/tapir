package sttp.tapir.generic.internal

import sttp.tapir.generic.Configuration
import sttp.tapir.{Codec, CodecFormat, EndpointIO}

import scala.reflect.macros.blackbox

trait FormCodecDerivation {
  implicit def formCaseClassCodec[T <: Product with Serializable](
      implicit conf: Configuration
  ): Codec[String, T, CodecFormat.XWwwFormUrlencoded] =
    macro FormCodecMacros.generateForCaseClass[T]
}

object FormCodecMacros {
  // http://blog.echo.sh/2013/11/04/exploring-scala-macros-map-to-case-class-conversion.html
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[Codec[String, T, CodecFormat.XWwwFormUrlencoded]] = {
    import c.universe._

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c)
    val fields = util.fields

    val fieldsWithCodecs = fields.map { field =>
      (field, c.typecheck(q"implicitly[sttp.tapir.Codec[List[String], ${field.typeSignature}, sttp.tapir.CodecFormat.TextPlain]]"))
    }

    val encodeParams: Iterable[Tree] = fieldsWithCodecs.map {
      case (field, codec) =>
        val fieldName = field.name.asInstanceOf[TermName]
        val fieldNameAsString = fieldName.decodedName.toString
        q"""val transformedName = $conf.toLowLevelName($fieldNameAsString)
            $codec.encode(o.$fieldName).map(v => (transformedName, v))"""
    }

    val decodeParams = fieldsWithCodecs.map {
      case (field, codec) =>
        val fieldName = field.name.decodedName.toString
        q"""val transformedName = $conf.toLowLevelName($fieldName)
            $codec.decode(paramsMap.get(transformedName).toList.flatten)"""
    }

    val codecTree = q"""
      {
        def decode(params: Seq[(String, String)]): sttp.tapir.DecodeResult[$t] = {
          val paramsMap: Map[String, Seq[String]] = params.groupBy(_._1).mapValues(_.map(_._2)).toMap
          val decodeResults = List(..$decodeParams)
          sttp.tapir.DecodeResult.sequence(decodeResults).map { values =>
            ${util.instanceFromValues}
          }
        }
        def encode(o: $t): Seq[(String, String)] = List(..$encodeParams).flatten

        sttp.tapir.Codec.formSeqCodecUtf8
          .mapDecode(decode _)(encode _)
          .schema(${util.schema})
          .validate(implicitly[sttp.tapir.Validator[$t]])
      }
     """
    Debug.logGeneratedCode(c)(t.typeSymbol.fullName, codecTree)
    c.Expr[Codec[String, T, CodecFormat.XWwwFormUrlencoded]](codecTree)
  }
}
