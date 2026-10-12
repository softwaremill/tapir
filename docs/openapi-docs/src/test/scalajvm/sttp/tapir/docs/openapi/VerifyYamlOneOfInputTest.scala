package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.model.MediaType
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir._

class VerifyYamlOneOfInputTest extends AnyFunSuite with Matchers {
  test("should support a required body with several content types") {
    val expectedYaml = load("oneOfInput/expected_required_json_png.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(VerifyYamlOneOfInputTest.setProfilePicture, Info("test", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should support an optional body with several content types") {
    val expectedYaml = load("oneOfInput/expected_optional_json_png.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(VerifyYamlOneOfInputTest.optionalProfilePicture, Info("test", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}

object VerifyYamlOneOfInputTest {
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

  val setProfilePicture = endpoint.put
    .in("set-profile-picture")
    .in(
      oneOfInput[ProfilePicture](
        oneOfInputVariant(jsonBody[LinkToExistingImage]) { case _: LinkToExistingImage => true },
        oneOfInputVariant(pngBody) { case _: PngImage => true }
      )
    )

  val optionalProfilePicture = endpoint.put
    .in("set-profile-picture")
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
}
