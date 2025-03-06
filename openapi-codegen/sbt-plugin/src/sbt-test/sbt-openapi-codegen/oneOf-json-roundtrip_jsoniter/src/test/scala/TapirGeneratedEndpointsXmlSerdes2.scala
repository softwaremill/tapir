import cats.xml.generic.Configuration
import cats.xml.generic.decoder.configured.semiauto.deriveConfiguredDecoder
import cats.xml.generic.encoder.configured.semiauto.deriveConfiguredEncoder
import cats.xml.utils.generic.{ParamName, TypeInfo}

object TapirGeneratedEndpointsXmlSerdes2 {
  import sttp.tapir.generated.TapirGeneratedEndpoints._
  import sttp.tapir.generic.auto._
  import cats.data.NonEmptyList
  import cats.xml.{NodeContent, Xml, XmlData, XmlNode}
  import cats.xml.codec.{Decoder, Encoder}
  import cats.xml.cursor.Cursor
  import cats.xml.generic.{XmlElemType, XmlTypeInterpreter}
  import cats.xml.syntax._

  private type XmlParseResult[T] = Either[Throwable, T]
  implicit val config: Configuration = Configuration.default.withUseLabelsForNodes(true)
  private def textDiscriminator[T]: (ParamName[T], TypeInfo[?]) => Boolean = (_, _) => false

  def relabeledNode[T](e: Encoder[T], label: String): Encoder[T] = new Encoder[T] {
    def encode(t: T): Xml = e.encode(t).asNode.map(_.withLabel(label)).get
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
  def seqEncoder[T: Encoder](nodeName: String, isWrapped: Boolean = true, itemName: String = "item"): Encoder[Seq[T]] =
    new Encoder[Seq[T]] {
      private val delegate = implicitly[Encoder[T]]

      override def encode(t: Seq[T]): Xml = if (isWrapped) {
        val content: NodeContent = NonEmptyList.fromList(t.map(delegate.encode).toList) match {
          case None => NodeContent.empty
          case Some(nel) =>
            nel.map(_.asNode) match {
              case n if n.forall(_.isDefined) => new NodeContent.Children(n.map(_.get.withLabel(itemName)))
              case n if n.forall(_.isEmpty) =>
                nel.map(_.asData) match {
                  case n if n.forall(_.isDefined) =>
                    NodeContent.children(n.map(_.get).toList.map(d => XmlNode(itemName, content = NodeContent.text(d))))
                  case n if n.exists(_.isDefined) => throw new IllegalStateException("Unable to encode heterogeneous lists")
                  case _ => throw new IllegalStateException(s"Unable to encode list with elements like: ${nel.head}")
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
                  case _ => throw new IllegalStateException(s"Unable to encode list with elements like: ${nel.head}")
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
    implicit lazy val CategoryXmlDecoder: Decoder[Category] = deriveConfiguredDecoder[Category]
    implicit lazy val TagXmlDecoder: Decoder[Tag] = deriveConfiguredDecoder[Tag]
    implicit val PetTagsSeqDecoder: Decoder[Seq[Tag]] = seqDecoder[Tag]("tags")
    implicit val PetPhotoUrlsSeqDecoder: Decoder[Seq[String]] = seqDecoder[String]("photoUrls")
    deriveConfiguredDecoder[Pet]
  }
  implicit lazy val PetXmlEncoder: Encoder[Pet] = {
    implicit val CategoryXmlEncoder: Encoder[Category] = deriveConfiguredEncoder[Category]
    implicit lazy val TagXmlEncoder: Encoder[Tag] = deriveConfiguredEncoder[Tag]
    implicit lazy val PetTagsSeqEncoder: Encoder[Seq[Tag]] = seqEncoder[Tag]("tags", itemName = "tag")
    implicit val PetPhotoUrlsSeqEncoder: Encoder[Seq[String]] = seqEncoder[String]("photoUrls")
    deriveConfiguredEncoder[Pet]
  }
  implicit lazy val PetXmlSerde: sttp.tapir.Codec.XmlCodec[Pet] =
    sttp.tapir.Codec.xml(xmlToDecodeResult[Pet])(_.toXml.toString)

  implicit lazy val TagXmlTypeInterpreter: XmlTypeInterpreter[Tag] = XmlTypeInterpreter.auto[Tag](textDiscriminator, (_, _) => false)

  implicit lazy val PetStatusXmlTypeInterpreter: XmlTypeInterpreter[PetStatus] =
    XmlTypeInterpreter.auto[PetStatus](textDiscriminator, (_, _) => false)
  implicit lazy val PetStatusXmlDecoder: Decoder[PetStatus] = implicitly[Decoder[String]].map(PetStatus.withName)
  implicit lazy val PetStatusXmlEncoder: Encoder[PetStatus] = implicitly[Encoder[String]].contramap(_.entryName)
  implicit lazy val PetStatusXmlSerde: sttp.tapir.Codec.XmlCodec[PetStatus] =
    sttp.tapir.Codec.xml(xmlToDecodeResult[PetStatus])(_.toXml.toString)

  implicit lazy val CategoryXmlTypeInterpreter: XmlTypeInterpreter[Category] =
    XmlTypeInterpreter.auto[Category](textDiscriminator, (_, _) => false)
}
