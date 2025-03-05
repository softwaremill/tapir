package sttp.tapir.generated

import cats.xml.utils.generic.{ParamName, TypeInfo}

object TapirGeneratedEndpointsXmlSerdes {
  import sttp.tapir.generated.TapirGeneratedEndpoints._
  import sttp.tapir.generic.auto._
  import cats.data.NonEmptyList
  import cats.xml.{NodeContent, Xml, XmlData, XmlNode}
  import cats.xml.codec.{Decoder, Encoder}
  import cats.xml.cursor.Cursor
  import cats.xml.generic.{XmlElemType, XmlTypeInterpreter}
  import cats.xml.syntax._
  import cats.xml.generic.decoder.semiauto._
  import cats.xml.generic.encoder.semiauto._

  private type XmlParseResult[T] = Either[Throwable, T]
  private def textDiscriminator[T]: (ParamName[T], TypeInfo[?]) => Boolean = (_, tpeInfo) =>
    false // tpeInfo.isString || tpeInfo.isOptionOfAnyPrimitiveOrString
//    tpeInfo.isOptionOfAnyPrimitiveOrString
//  implicit val mkOptionXmlTypeInterpreter: XmlTypeInterpreter[Option[?]] = XmlTypeInterpreter.auto[Option[?]](
//    textDiscriminator, (_, _) => false)
  implicit def optionDecoder[T: Decoder]: Decoder[Option[T]] = new Decoder[Option[T]] {
    private val delegate = implicitly[Decoder[T]]

    override def decodeCursorResult(cursorResult: Cursor.Result[Xml]): Decoder.Result[Option[T]] = cursorResult match {
      case Right(x) if x.isNull   => cats.data.Validated.Valid(None)
      case Left(e) if e.isMissing => cats.data.Validated.Valid(None)
      case o                      => delegate.decodeCursorResult(o).map(Some(_))
    }
  }
  implicit def optionEncoder[T: Encoder]: Encoder[Option[T]] = new Encoder[Option[T]] {
    private val delegate = implicitly[Encoder[T]]

    override def encode(t: Option[T]): Xml = t match {
      case None    => Xml.Null
      case Some(t) => delegate.encode(t)
    }
  }
  def seqDecoder[T: Decoder](nodeName: String): Decoder[Seq[T]] = new Decoder[Seq[T]] {
    private val delegate = implicitly[Decoder[T]]

    def decodeCursorResult(cursorResult: Cursor.Result[Xml]): Decoder.Result[Seq[T]] = cursorResult match {
      case Right(x: XmlNode) if x.label == nodeName =>
        x.content match {
          case NodeContent.Children(c) => c.traverse(delegate.decode).map(_.toList)
          case NodeContent.Empty       => cats.data.Validated.Valid(Nil)
        }
      case Left(errs) => cats.data.Validated.Invalid(NonEmptyList.one(cats.xml.codec.DecoderFailure.CursorFailed(errs)))
    }
  }
  def seqEncoder[T: Encoder](nodeName: String, isWrapped: Boolean = true): Encoder[Seq[T]] = new Encoder[Seq[T]] {
    private val delegate = implicitly[Encoder[T]]

    override def encode(t: Seq[T]): Xml = if (isWrapped) {
      val content: NodeContent = NonEmptyList.fromList(t.map(delegate.encode).toList) match {
        case None => NodeContent.empty
        case Some(nel) =>
          nel.map(_.asNode) match {
            case n if n.forall(_.isDefined) => new NodeContent.Children(n.map(_.get))
            case n if n.forall(_.isEmpty) =>
              nel.map(_.asData) match {
                case n if n.forall(_.isDefined) =>
                  NodeContent.children(n.map(_.get).toList.map(d => XmlNode("item", content = NodeContent.text(d))))
                case n if n.exists(_.isDefined) => throw new IllegalStateException("Unable to encode heterogeneous lists")
                case _                          => throw new IllegalStateException(s"Unable to encode list with elements like: ${nel.head}")
              }
            case _ => throw new IllegalStateException("Unable to encode heterogeneous lists")
          }
      }
      XmlNode(nodeName, content = content)
    } else {
      NonEmptyList.fromList(t.map(delegate.encode).toList) match {
        case None => Xml.Null
        case Some(nel) =>
          nel.map(_.asNode) match {
            case n if n.forall(_.isDefined) =>
              XmlNode.group(n.toList.map(d => XmlNode(nodeName, content = NodeContent.children(Seq(d.get)))))
            case n if n.forall(_.isEmpty) =>
              nel.map(_.asData) match {
                case n if n.forall(_.isDefined) =>
                  XmlNode.group(n.map(_.get).toList.map(d => XmlNode(nodeName, content = NodeContent.text(d))))
                case n if n.exists(_.isDefined) => throw new IllegalStateException("Unable to encode heterogeneous lists")
                case _                          => throw new IllegalStateException(s"Unable to encode list with elements like: ${nel.head}")
              }
            case _ => throw new IllegalStateException("Unable to encode heterogeneous lists")
          }
      }

    }
  }
  def xmlToDecodeResult[T: Decoder](s: String): sttp.tapir.DecodeResult[T] = s.parseXml[XmlParseResult] match {
    case Right(xml: XmlNode) =>
      xml.as[T] match {
        case cats.data.Validated.Invalid(e) => sttp.tapir.DecodeResult.Multiple(e.toList)
        case cats.data.Validated.Valid(v)   => sttp.tapir.DecodeResult.Value(v)
      }
    case Left(t) => sttp.tapir.DecodeResult.Error(s, t)
  }

  implicit lazy val PetXmlTypeInterpreter: XmlTypeInterpreter[Pet] = XmlTypeInterpreter.auto[Pet](textDiscriminator, (_, _) => false)
  implicit lazy val PetXmlDecoder: Decoder[Pet] = {
    implicit val PetTagsSeqDecoder: Decoder[Seq[Tag]] = seqDecoder[Tag]("tags")
    implicit val PetPhotoUrlsSeqDecoder: Decoder[Seq[String]] = seqDecoder[String]("photoUrls")
    deriveDecoder[Pet]
  }
  implicit lazy val PetXmlEncoder: Encoder[Pet] = {
    implicit val PetTagsSeqEncoder: Encoder[Seq[Tag]] = seqEncoder[Tag]("tags")
    implicit val PetPhotoUrlsSeqEncoder: Encoder[Seq[String]] = seqEncoder[String]("photoUrls")
    deriveEncoder[Pet]
  }
  implicit lazy val PetXmlSerde: sttp.tapir.Codec.XmlCodec[Pet] =
    sttp.tapir.Codec.xml(xmlToDecodeResult[Pet])(_.toXml.toString)

  implicit lazy val TagXmlTypeInterpreter: XmlTypeInterpreter[Tag] = XmlTypeInterpreter.auto[Tag](textDiscriminator, (_, _) => false)
  implicit lazy val TagXmlDecoder: Decoder[Tag] = deriveDecoder[Tag]
  implicit lazy val TagXmlEncoder: Encoder[Tag] = deriveEncoder[Tag]
  implicit lazy val TagXmlSerde: sttp.tapir.Codec.XmlCodec[Tag] =
    sttp.tapir.Codec.xml(xmlToDecodeResult[Tag])(_.toXml.toString)

  implicit lazy val PetStatusXmlTypeInterpreter: XmlTypeInterpreter[PetStatus] =
    XmlTypeInterpreter.auto[PetStatus](textDiscriminator, (_, _) => false)
  implicit lazy val PetStatusXmlDecoder: Decoder[PetStatus] = deriveDecoder[PetStatus]
  implicit lazy val PetStatusXmlEncoder: Encoder[PetStatus] = deriveEncoder[PetStatus]
  implicit lazy val PetStatusXmlSerde: sttp.tapir.Codec.XmlCodec[PetStatus] =
    sttp.tapir.Codec.xml(xmlToDecodeResult[PetStatus])(_.toXml.toString)

  implicit lazy val CategoryXmlTypeInterpreter: XmlTypeInterpreter[Category] =
    XmlTypeInterpreter.auto[Category](textDiscriminator, (_, _) => false)
  implicit lazy val CategoryXmlDecoder: Decoder[Category] = deriveDecoder[Category]
  implicit lazy val CategoryXmlEncoder: Encoder[Category] = deriveEncoder[Category]
  implicit lazy val CategoryXmlSerde: sttp.tapir.Codec.XmlCodec[Category] =
    sttp.tapir.Codec.xml(xmlToDecodeResult[Category])(_.toXml.toString)
}
