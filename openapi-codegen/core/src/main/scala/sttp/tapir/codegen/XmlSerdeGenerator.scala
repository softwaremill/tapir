package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.XmlSerdeLib.XmlSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaEnum,
  OpenapiSchemaField,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType
}
import sttp.tapir.codegen.openapi.models.OpenapiXml

object XmlSerdeGenerator {

  def generateSerdes(xmlSerdeLib: XmlSerdeLib, doc: OpenapiDocument, xmlParamRefs: Set[String], targetScala3: Boolean): Option[String] = {
    if (xmlParamRefs.isEmpty || xmlSerdeLib == XmlSerdeLib.NoSupport) None
    else
      Some {
        xmlParamRefs
          .map { ref =>
            val decoderName = s"${ref}XmlDecoder"
            val encoderName = s"${ref}XmlEncoder"
            val mappedArraysOfSimpleSchemas = doc.components.toSeq
              .flatMap(_.schemas.get(ref))
              .collect { case OpenapiSchemaObject(props, required, _, _) => props.map(p => p -> required.contains(p._1)) }
              .flatMap {
                _.collect {
                  case ((n, OpenapiSchemaField(t: OpenapiSchemaRef, _)), r)
                      if doc.components.exists(_.schemas.get(t.stripped).exists(_.isInstanceOf[OpenapiSchemaEnum])) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    val d = if (!r || t.nullable) s"Option[$tpe]" else tpe
                    (n, d, tpe, 2, None)
                  case ((n, OpenapiSchemaField(OpenapiSchemaArray(t: OpenapiSchemaSimpleType, _, maybeXml), _)), _) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    (n, tpe, tpe, 0, maybeXml)
                  case ((n, OpenapiSchemaField(t: OpenapiSchemaRef, _)), r) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    val d = if (!r || t.nullable) s"Option[$tpe]" else tpe
                    (n, d, tpe, 1, None)
                }
              }
              .distinct
            // TODO: parse `xml` on schema and use it to configure these
            def decoderFor(tpe: String) = if (targetScala3) s"$tpe.valueOf" else tpe
            val maybeElemSeqDecoders = mappedArraysOfSimpleSchemas
              .map {
                case (n, t, _, 0, c: Option[OpenapiXml.XmlArrayConfiguration @unchecked]) =>
                  val name = c.flatMap(_.name).getOrElse(n)
                  val w = c.exists(_.isWrapped)
                  s"""implicit val $ref${n.capitalize}SeqDecoder: Decoder[Seq[$t]] = seqDecoder[$t]("$name", isWrapped = $w)"""
                case (n, t, _, 1, _) => s"""// implicit val $ref${n.capitalize}Decoder: Decoder[$t] = deriveConfiguredDecoder[$t]"""
                case (n, t, tpe, 2, _) if t == tpe =>
                  s"""implicit val $ref${n.capitalize}Decoder: Decoder[$t] = enumDecoder(${decoderFor(s"$ref${n.capitalize}")})"""
                case (n, t, tpe, 2, _) =>
                  s"""implicit val $ref${n.capitalize}OptionDecoder: Decoder[$t] = optionDecoder[$tpe](enumDecoder[$tpe](${decoderFor(
                      tpe
                    )}))""".stripMargin
              } match {
              case s if s.isEmpty => None
              case s              => Some(s.mkString("\n"))
            }
            val maybeElemSeqEncoders = mappedArraysOfSimpleSchemas
              .map {
                case (n, t, _, 0, c: Option[OpenapiXml.XmlArrayConfiguration @unchecked]) =>
                  // TODO: Parameterisation here must come from openapi
                  val in = c.flatMap(_.itemName).getOrElse(n)
                  val w = c.exists(_.isWrapped)
                  s"""implicit val $ref${n.capitalize}SeqEncoder: Encoder[Seq[$t]] =
                     |  seqEncoder[$t]("${c.flatMap(_.name).getOrElse(n)}", isWrapped = $w, itemName = "$in")""".stripMargin
                case (n, t, _, 1, _) =>
                  s"""implicit val $ref${n.capitalize}Encoder: Encoder[$t] = deriveConfiguredEncoder[$t]""".stripMargin
                case (n, t, tpe, 2, _) if t == tpe =>
                  s"""implicit val $ref${n.capitalize}Encoder: Encoder[$tpe] = enumEncoder[$tpe]("$n")""".stripMargin
                case (n, t, tpe, 2, _) =>
                  s"""implicit val $ref${n.capitalize}Encoder: Encoder[$tpe] = enumEncoder[$tpe]("$n")
                     |implicit val $ref${n.capitalize}OptionEncoder: Encoder[$t] = optionEncoder[$tpe]($ref${n.capitalize}Encoder)""".stripMargin
              } match {
              case s if s.isEmpty => None
              case s              => Some(s.mkString("\n"))
            }
            val decoderDefn = maybeElemSeqDecoders match {
              case None => s"deriveConfiguredDecoder[$ref]"
              case Some(e) =>
                s"""{
                   |${indent(2)(e)}
                   |  deriveConfiguredDecoder[$ref]
                   |}""".stripMargin
            }
            val encoderDefn = maybeElemSeqEncoders match {
              case None => s"deriveConfiguredEncoder[$ref]"
              case Some(e) =>
                s"""{
                   |${indent(2)(e)}
                   |  deriveConfiguredEncoder[$ref]
                   |}""".stripMargin
            }
            // TODO: Should be able to rename non-array fields too
            val renamedFields = mappedArraysOfSimpleSchemas
              .collect { case (n, t, _, 0, c: Option[OpenapiXml.XmlArrayConfiguration @unchecked]) =>
                n -> c.flatMap(_.name)
              }
              .collect { case (n, Some(n2)) if n != n2 => n -> n2 }
            val interpreter =
              if (renamedFields.nonEmpty) {
                val cases = renamedFields
                  .map { case (from, to) =>
                    s"""case (cats.xml.utils.generic.ParamName("$from"), _) =>
                     |  (XmlElemType.Child, { case "$from" => "$to"; case "$to" => "$from"; case x => x})""".stripMargin
                  }
                  .mkString("\n")
                s"""XmlTypeInterpreter.fullOf[$ref]{
                   |${indent(4)(cases)}
                   |    case (_, _) => (XmlElemType.Child, identity)
                   |  }""".stripMargin
              } else s"""XmlTypeInterpreter.auto[$ref]((_, _) => false, (_, _) => false)""".stripMargin
            s"""
               |implicit lazy val ${ref}XmlTypeInterpreter: XmlTypeInterpreter[$ref] = $interpreter
               |implicit lazy val $decoderName: Decoder[$ref] = $decoderDefn
               |implicit lazy val $encoderName: Encoder[$ref] = $encoderDefn
               |implicit lazy val ${ref}XmlSerde: sttp.tapir.Codec.XmlCodec[${ref.capitalize}] =
               |  sttp.tapir.Codec.xml(xmlToDecodeResult[$ref])(_.toXml.toString)""".stripMargin
          }
          .mkString("\n")
      }
  }

  def wrapBody(xmlSerdeLib: XmlSerdeLib, packagePath: String, objName: String, targetScala3: Boolean, body: String): String = xmlSerdeLib match {
    case XmlSerdeLib.NoSupport =>
      throw new IllegalStateException("Codegen should not be attempting to generate serdes when specified xml lib is 'none'")
    case XmlSerdeLib.CatsXml =>
      val enumDecoder =
        if (targetScala3)
          """  def enumDecoder[T: scala.reflect.ClassTag](fn: String => T): Decoder[T] =
        |    Decoder.instance { case x: XmlNode.Node =>
        |      x.content match {
        |        case NodeContent.Text(t) =>
        |          scala.util.Try(fn(t.asString)) match {
        |            case scala.util.Success(v) => cats.data.Validated.Valid(v)
        |            case scala.util.Failure(f) =>
        |              cats.data.Validated.Invalid(NonEmptyList.one(cats.xml.codec.DecoderFailure.UnableToDecodeType(f)))
        |          }
        |        case _ => cats.data.Validated.Invalid(NonEmptyList.one(cats.xml.codec.DecoderFailure.NoTextAvailable(x)))
        |      }
        |    }
        |  def enumEncoder[T](label: String): Encoder[T] =
        |    cats.xml.codec.Encoder.of(x => XmlNode(label, Nil, content = NodeContent.text(x.toString)))""".stripMargin
        else
          """  def enumDecoder[T <: enumeratum.EnumEntry: scala.reflect.ClassTag](e: enumeratum.Enum[T]): Decoder[T] =
        |    Decoder.instance { case x: XmlNode.Node =>
        |      x.content match {
        |        case NodeContent.Text(t) =>
        |          scala.util.Try(e.withName(t.asString)) match {
        |            case scala.util.Success(v) => cats.data.Validated.Valid(v)
        |            case scala.util.Failure(f) =>
        |              cats.data.Validated.Invalid(NonEmptyList.one(cats.xml.codec.DecoderFailure.UnableToDecodeType(f)))
        |          }
        |        case _ => cats.data.Validated.Invalid(NonEmptyList.one(cats.xml.codec.DecoderFailure.NoTextAvailable(x)))
        |      }
        |    }
        |  def enumEncoder[T <: enumeratum.EnumEntry](label: String): Encoder[T] =
        |    cats.xml.codec.Encoder.of(x => XmlNode(label, Nil, content = NodeContent.text(x.entryName)))""".stripMargin
      s"""package $packagePath
       |
       |object ${objName}XmlSerdes {
       |  import $packagePath.$objName._
       |  import sttp.tapir.generic.auto._
       |  import cats.data.NonEmptyList
       |  import cats.xml.{NodeContent, Xml, XmlData, XmlNode}
       |  import cats.xml.codec.{Decoder, Encoder}
       |  import cats.xml.cursor.Cursor
       |  import cats.xml.generic.{XmlElemType, XmlTypeInterpreter}
       |  import cats.xml.syntax._
       |  import cats.xml.generic.decoder.configured.semiauto._
       |  import cats.xml.generic.encoder.configured.semiauto._
       |
       |  private type XmlParseResult[T] = Either[Throwable, T]
       |  implicit val config: cats.xml.generic.Configuration = cats.xml.generic.Configuration.default.withUseLabelsForNodes(true)
       |  implicit val mkOptionXmlTypeInterpreter: XmlTypeInterpreter[Option[?]] = XmlTypeInterpreter.auto[Option[?]](
       |    (_, _) => false, (_, _) => false)
       |$enumDecoder
       |  implicit def optionDecoder[T: Decoder]: Decoder[Option[T]] = new Decoder[Option[T]] {
       |    private val delegate = implicitly[Decoder[T]]
       |
       |    override def decodeCursorResult(cursorResult: Cursor.Result[Xml]): Decoder.Result[Option[T]] = cursorResult match {
       |      case Right(x) if x.isNull => cats.data.Validated.Valid(None)
       |      case Left(e) if e.isMissing => cats.data.Validated.Valid(None)
       |      case o => delegate.decodeCursorResult(o).map(Some(_))
       |    }
       |  }
       |  implicit def optionEncoder[T: Encoder]: Encoder[Option[T]] = new Encoder[Option[T]] {
       |    private val delegate = implicitly[Encoder[T]]
       |
       |    override def encode(t: Option[T]): Xml = t match {
       |      case None => Xml.Null
       |      case Some(t) => delegate.encode(t)
       |    }
       |  }
       |  def seqDecoder[T: Decoder](nodeName: String, isWrapped: Boolean = true): Decoder[Seq[T]] = new Decoder[Seq[T]] {
       |    private val delegate = implicitly[Decoder[T]]
       |
       |    def decodeCursorResult(cursorResult: Cursor.Result[Xml]): Decoder.Result[Seq[T]] = cursorResult match {
       |      case Right(x: XmlNode) if isWrapped =>
       |        x.content match {
       |          case NodeContent.Children(c) => c.traverse(delegate.decode).map(_.toList)
       |          case NodeContent.Empty       => cats.data.Validated.Valid(Nil)
       |        }
       |      case Right(x: XmlNode.Group) if !isWrapped =>
       |       NonEmptyList.fromList(x.children).map(_.traverse(delegate.decode).map(_.toList)).getOrElse(cats.data.Validated.Valid(Nil))
       |      case Right(x: XmlNode.Node) if !isWrapped =>
       |       delegate.decode(x).map(List(_))
       |      case Left(errs) => cats.data.Validated.Invalid(NonEmptyList.one(cats.xml.codec.DecoderFailure.CursorFailed(errs)))
       |    }
       |  }
       |  def seqEncoder[T: Encoder](nodeName: String, isWrapped: Boolean = true, itemName: String = "item"): Encoder[Seq[T]] =
       |    new Encoder[Seq[T]] {
       |      private val delegate = implicitly[Encoder[T]]
       |
       |      override def encode(t: Seq[T]): Xml = if (isWrapped) {
       |        val content: NodeContent = NonEmptyList.fromList(t.map(delegate.encode).toList) match {
       |          case None => NodeContent.empty
       |          case Some(nel) =>
       |            nel.map(_.asNode) match {
       |              case n if n.forall(_.isDefined) => new NodeContent.Children(n.map(_.get.withLabel(itemName)))
       |              case n if n.forall(_.isEmpty) =>
       |                nel.map(_.asData) match {
       |                  case n if n.forall(_.isDefined) =>
       |                    NodeContent.children(n.map(_.get).toList.map(d => XmlNode(itemName, content = NodeContent.text(d))))
       |                  case n if n.exists(_.isDefined) => throw new IllegalStateException("Unable to encode heterogeneous lists")
       |                  case _ => throw new IllegalStateException(s"Unable to encode list with elements like: $${nel.head}")
       |                }
       |              case _ => throw new IllegalStateException("Unable to encode heterogeneous lists")
       |            }
       |        }
       |        XmlNode(nodeName, content = content)
       |      } else {
       |        NonEmptyList.fromList(t.map(delegate.encode).toList) match {
       |          case None => Xml.Null
       |          case Some(nel) =>
       |            nel.map(_.asNode) match {
       |              case n if n.forall(_.isDefined) =>
       |                XmlNode.group(n.toList.map(_.get.withLabel(nodeName)))
       |              case n if n.forall(_.isEmpty) =>
       |                nel.map(_.asData) match {
       |                  case n if n.forall(_.isDefined) =>
       |                    XmlNode.group(n.map(_.get).toList.map(d => XmlNode(nodeName, content = NodeContent.text(d))))
       |                  case n if n.exists(_.isDefined) => throw new IllegalStateException("Unable to encode heterogeneous lists")
       |                  case _ => throw new IllegalStateException(s"Unable to encode list with elements like: $${nel.head}")
       |                }
       |              case _ => throw new IllegalStateException("Unable to encode heterogeneous lists")
       |            }
       |        }
       |
       |      }
       |    }
       |  def xmlToDecodeResult[T: Decoder](s: String): sttp.tapir.DecodeResult[T] = s.parseXml[XmlParseResult] match {
       |    case Right(xml: XmlNode) => xml.as[T] match {
       |      case cats.data.Validated.Invalid(e) => sttp.tapir.DecodeResult.Multiple(e.toList)
       |      case cats.data.Validated.Valid(v) => sttp.tapir.DecodeResult.Value(v)
       |    }
       |    case Left(t) => sttp.tapir.DecodeResult.Error(s, t)
       |  }
       |${indent(2)(body)}
       |}""".stripMargin
  }
}
