package tapir.generic

import tapir.{Codec, MediaType}

import scala.reflect.macros.blackbox

trait FormCodecDerivation {
  implicit def formCaseClassCodec[T <: Product with Serializable](
      implicit conf: Configuration
  ): Codec[T, MediaType.XWwwFormUrlencoded, String] =
    macro FormCodecMacros.generateForCaseClass[T]
}

object FormCodecMacros {
  // http://blog.echo.sh/2013/11/04/exploring-scala-macros-map-to-case-class-conversion.html
  def generateForCaseClass[T: c.WeakTypeTag](
      c: blackbox.Context
  )(conf: c.Expr[Configuration]): c.Expr[Codec[T, MediaType.XWwwFormUrlencoded, String]] = {
    import c.universe._

    val t = weakTypeOf[T]
    val util = new CaseClassUtil[c.type, T](c)
    val fields = util.fields

    val fieldsWithCodecs = fields.map { field =>
      (field, c.typecheck(q"implicitly[tapir.CodecForMany.PlainCodecForMany[${field.typeSignature}]]"))
    }

    val encodeParams: Iterable[Tree] = fieldsWithCodecs.map {
      case (field, codec) =>
        val fieldName = field.name.asInstanceOf[TermName]
        val fieldNameAsString = fieldName.decodedName.toString
        q"""val transformedName = $conf.transformMemberName($fieldNameAsString)
           $codec.encode(o.$fieldName).map(v => (transformedName, v))"""
    }

    val decodeParams = fieldsWithCodecs.map {
      case (field, codec) =>
        val fieldName = field.name.decodedName.toString
        q"""val transformedName = $conf.transformMemberName($fieldName)
           $codec.decode(paramsMap.get(transformedName).toList.flatten)"""
    }

    val codecTree = q"""
      {
        def decode(params: Seq[(String, String)]): DecodeResult[$t] = {
          val paramsMap: Map[String, Seq[String]] = params.groupBy(_._1).mapValues(_.map(_._2)).toMap
          val decodeResults = List(..$decodeParams)
          tapir.DecodeResult.sequence(decodeResults).map { values =>
            ${util.instanceFromValues}
          }
        }
        def encode(o: $t): Seq[(String, String)] = List(..$encodeParams).flatten

        tapir.Codec.formSeqCodecUtf8
          .mapDecode(decode _)(encode _)
          .schema(${util.schema}.schema)
          .validate(implicitly[tapir.Validator[$t]])
      }
     """
    Debug.logGeneratedCode(c)(t.typeSymbol.fullName, codecTree)
    c.Expr[Codec[T, MediaType.XWwwFormUrlencoded, String]](codecTree)
  }
}
