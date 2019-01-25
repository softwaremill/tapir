package tapir.generic

import tapir.{Codec, MediaType}

import scala.reflect.macros.blackbox

trait FormCodecDerivation {
  implicit def formCaseClassCodec[T <: Product with Serializable](
      implicit conf: Configuration): Codec[T, MediaType.XWwwFormUrlencoded, String] =
    macro FormCodecMacros.generateForCaseClass[T]
}

object FormCodecMacros {
  // http://blog.echo.sh/2013/11/04/exploring-scala-macros-map-to-case-class-conversion.html
  def generateForCaseClass[T: c.WeakTypeTag](c: blackbox.Context)(
      conf: c.Expr[Configuration]): c.Expr[Codec[T, MediaType.XWwwFormUrlencoded, String]] = {
    import c.universe._

    val t = weakTypeOf[T]
    if (!t.typeSymbol.isClass || !t.typeSymbol.asClass.isCaseClass) {
      c.error(c.enclosingPosition, s"Form data codec can only be generated for a case class, but got: $t.")
    }

    val fields = t.decls
      .collectFirst {
        case m: MethodSymbol if m.isPrimaryConstructor => m
      }
      .get
      .paramLists
      .head

    val fieldsWithCodecs = fields.map { field =>
      (field, c.typecheck(q"implicitly[tapir.CodecFromMany.PlainCodecFromMany[${field.typeSignature}]]"))
    }

    val schema = c.typecheck(q"implicitly[tapir.SchemaFor[$t]]")

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

    val companion = Ident(TermName(t.typeSymbol.name.decodedName.toString))

    val instanceFromValues = if (fields.size == 1) {
      q"$companion.apply(vs.head.asInstanceOf[${fields.head.typeSignature}])"
    } else {
      q"$companion.tupled.asInstanceOf[Any => $t].apply(tapir.internal.SeqToParams(vs))"
    }

    val codecTree = q"""
      {
        def decode(params: Seq[(String, String)]): DecodeResult[$t] = {
          val paramsMap: Map[String, Seq[String]] = params.groupBy(_._1).mapValues(_.map(_._2))
          val decodeResults = List(..$decodeParams)
          tapir.DecodeResult.sequence(decodeResults).map { vs =>
            $instanceFromValues
          }
        }
        def encode(o: $t): Seq[(String, String)] = List(..$encodeParams).flatten

        tapir.Codec.formSeqCodecUtf8
          .mapDecode(decode _)(encode _)
          .schema($schema.schema)
      }
     """

    c.Expr[Codec[T, MediaType.XWwwFormUrlencoded, String]](codecTree)
  }
}
