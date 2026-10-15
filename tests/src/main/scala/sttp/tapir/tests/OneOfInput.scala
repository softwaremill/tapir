package sttp.tapir.tests

import io.circe.generic.auto._
import sttp.model.MediaType
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object OneOfInput {
  sealed trait ProfilePicture
  case class LinkToExistingImage(href: String) extends ProfilePicture
  case class PngImage(bytes: Array[Byte]) extends ProfilePicture

  private case class ImagePng() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ImagePng
  }

  private val pngBody: EndpointIO.Body[Array[Byte], PngImage] =
    EndpointIO
      .Body(RawBodyType.ByteArrayBody, Codec.id(ImagePng(), Schema.schemaForByteArray), EndpointIO.Info.empty)
      .map(PngImage(_))(_.bytes)

  val in_json_or_png_out_string: PublicEndpoint[ProfilePicture, Unit, String, Any] =
    endpoint.post
      .in("one-of-input")
      .in(
        oneOfInput[ProfilePicture](
          oneOfInputVariant(jsonBody[LinkToExistingImage]) { case _: LinkToExistingImage => true },
          oneOfInputVariant(pngBody) { case _: PngImage => true }
        )
      )
      .out(stringBody)

  val in_optional_json_or_png_out_string: PublicEndpoint[Option[ProfilePicture], Unit, String, Any] =
    endpoint.post
      .in("one-of-input-optional")
      .in(
        oneOfInput[Option[ProfilePicture]](
          oneOfInputVariant(emptyInputAs((None: Option[ProfilePicture]))) { case None => true },
          oneOfInputVariant(
            jsonBody[LinkToExistingImage].map(link => (Some(link): Option[ProfilePicture]))(_.get.asInstanceOf[LinkToExistingImage])
          ) { case Some(_: LinkToExistingImage) => true },
          oneOfInputVariant(pngBody.map(png => (Some(png): Option[ProfilePicture]))(_.get.asInstanceOf[PngImage])) {
            case Some(_: PngImage) => true
          }
        )
      )
      .out(stringBody)
}
