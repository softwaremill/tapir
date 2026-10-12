package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.MediaType

class OneOfInputTest extends AnyFlatSpec with Matchers {
  private val optionalString = oneOfInput[Option[String]](
    oneOfInputVariant(emptyInputAs((None: Option[String]))) { case None => true },
    oneOfInputVariant(stringBody.map(Some(_))(_.get)) { case Some(_) => true }
  )

  // requestBody required, with application/json and image/png alternatives
  sealed trait ProfilePicture
  case class LinkToExistingImage(href: String) extends ProfilePicture
  case class PngImage(bytes: Array[Byte]) extends ProfilePicture

  private val jsonBody: EndpointIO.Body[String, LinkToExistingImage] =
    stringJsonBody.map(LinkToExistingImage(_))(link => s"""{"href":"${link.href}"}""")
  private case class ImagePng() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ImagePng
  }
  private val pngBody: EndpointIO.Body[Array[Byte], PngImage] =
    EndpointIO
      .Body(RawBodyType.ByteArrayBody, Codec.id(ImagePng(), Schema.schemaForByteArray), EndpointIO.Info.empty)
      .map(PngImage(_))(_.bytes)
  private val profilePicture = oneOfInput[ProfilePicture](
    oneOfInputVariant(jsonBody) { case _: LinkToExistingImage => true },
    oneOfInputVariant(pngBody) { case _: PngImage => true }
  )

  // same alternatives, but the body may be omitted: None / Some(json) / Some(png)
  private val optionalProfilePicture = oneOfInput[Option[ProfilePicture]](
    oneOfInputVariant(emptyInputAs((None: Option[ProfilePicture]))) { case None => true },
    oneOfInputVariant(jsonBody.map(link => (Some(link): Option[ProfilePicture]))(_.get.asInstanceOf[LinkToExistingImage])) {
      case Some(_: LinkToExistingImage) => true
    },
    oneOfInputVariant(pngBody.map(png => (Some(png): Option[ProfilePicture]))(_.get.asInstanceOf[PngImage])) {
      case Some(_: PngImage) => true
    }
  )

  "oneOfInput" should "encode None with the empty variant" in {
    val (chosen, low) = optionalString.variantForValue(None)
    chosen shouldBe a[EndpointIO.Empty[_]]
    low shouldBe None
  }

  it should "encode Some with the body variant, unwrapping before the body codec" in {
    val (chosen, low) = optionalString.variantForValue(Some("hello"))
    low shouldBe Some("hello")
    val body = chosen.asInstanceOf[EndpointIO.Body[String, Option[String]]]
    body.codec.encode(Some("hello")) shouldBe "hello"
  }

  it should "reject variants that are not a body or an empty input" in {
    assertDoesNotCompile("""
      import sttp.tapir._
      oneOfInput[String](oneOfVariant(statusCode(sttp.model.StatusCode.Ok).map(_ => "ok")(_ => ())))
    """)
    assertDoesNotCompile("""
      import sttp.tapir._
      oneOfInput[String](oneOfInputVariant(header[String]("X-Token")) { case _ => true })
    """)
  }

  it should "encode a json alternative and a binary alternative with their own codecs" in {
    val link = LinkToExistingImage("https://cdn.example/a.png")
    val (jsonInput, jsonLow) = profilePicture.variantForValue(link)
    val json = jsonInput.asInstanceOf[EndpointIO.Body[String, LinkToExistingImage]]
    json.codec.format.mediaType shouldBe MediaType.ApplicationJson
    json.codec.encode(jsonLow.asInstanceOf[LinkToExistingImage]) shouldBe """{"href":"https://cdn.example/a.png"}"""

    val bytes = Array[Byte](1, 2, 3)
    val (pngInput, pngLow) = profilePicture.variantForValue(PngImage(bytes))
    val png = pngInput.asInstanceOf[EndpointIO.Body[Array[Byte], PngImage]]
    png.codec.format.mediaType shouldBe MediaType.ImagePng
    png.codec.encode(pngLow.asInstanceOf[PngImage]).toSeq shouldBe bytes.toSeq
  }

  it should "decode a json alternative and a binary alternative by content type" in {
    val json = profilePicture.variantForDecode(Some(MediaType.ApplicationJson), None)
    json.get.asInstanceOf[EndpointIO.Body[String, LinkToExistingImage]].codec.format.mediaType shouldBe MediaType.ApplicationJson

    val png = profilePicture.variantForDecode(Some(MediaType.ImagePng), None)
    png.get.asInstanceOf[EndpointIO.Body[Array[Byte], PngImage]].codec.format.mediaType shouldBe MediaType.ImagePng

    // charset on the request still matches the body's media type
    val jsonWithCharset = profilePicture.variantForDecode(Some(MediaType.ApplicationJson.charset("utf-8")), None)
    jsonWithCharset.get.asInstanceOf[EndpointIO.Body[String, LinkToExistingImage]].codec.format.mediaType shouldBe MediaType.ApplicationJson

    profilePicture.variantForDecode(Some(MediaType.TextPlain), None) shouldBe None

    // no empty variant: a zero-length body with a matching content type is still that body
    val emptyJson = profilePicture.variantForDecode(Some(MediaType.ApplicationJson), Some("0"))
    emptyJson.get.asInstanceOf[EndpointIO.Body[String, LinkToExistingImage]].codec.format.mediaType shouldBe MediaType.ApplicationJson
    val emptyPng = profilePicture.variantForDecode(Some(MediaType.ImagePng), Some("0"))
    emptyPng.get.asInstanceOf[EndpointIO.Body[Array[Byte], PngImage]].codec.format.mediaType shouldBe MediaType.ImagePng
  }

  it should "encode None, Some(json) and Some(png) with different variants" in {
    val (empty, none) = optionalProfilePicture.variantForValue(None)
    empty shouldBe a[EndpointIO.Empty[_]]
    none shouldBe None

    val link = LinkToExistingImage("https://cdn.example/a.png")
    val (jsonInput, jsonLow) = optionalProfilePicture.variantForValue(Some(link))
    jsonLow shouldBe Some(link)
    val json = jsonInput.asInstanceOf[EndpointIO.Body[String, Option[ProfilePicture]]]
    json.codec.format.mediaType shouldBe MediaType.ApplicationJson
    json.codec.encode(Some(link)) shouldBe """{"href":"https://cdn.example/a.png"}"""

    val bytes = Array[Byte](1, 2, 3)
    val pngValue = PngImage(bytes)
    val (pngInput, pngLow) = optionalProfilePicture.variantForValue(Some(pngValue))
    pngLow shouldBe Some(pngValue)
    val png = pngInput.asInstanceOf[EndpointIO.Body[Array[Byte], Option[ProfilePicture]]]
    png.codec.format.mediaType shouldBe MediaType.ImagePng
    png.codec.encode(Some(pngValue)).toSeq shouldBe bytes.toSeq
  }

  it should "decode an optional json-or-png body by content type, and a missing body as empty" in {
    optionalProfilePicture.variantForDecode(None, None).get shouldBe a[EndpointIO.Empty[_]]
    optionalProfilePicture.variantForDecode(None, Some("0")).get shouldBe a[EndpointIO.Empty[_]]

    val json = optionalProfilePicture.variantForDecode(Some(MediaType.ApplicationJson), None)
    json.get.asInstanceOf[EndpointIO.Body[String, Option[ProfilePicture]]].codec.format.mediaType shouldBe MediaType.ApplicationJson

    val png = optionalProfilePicture.variantForDecode(Some(MediaType.ImagePng), None)
    png.get.asInstanceOf[EndpointIO.Body[Array[Byte], Option[ProfilePicture]]].codec.format.mediaType shouldBe MediaType.ImagePng

    // a content type that matches neither body is a failure, not the empty variant
    optionalProfilePicture.variantForDecode(Some(MediaType.TextPlain), None) shouldBe None
    optionalProfilePicture.variantForDecode(Some(MediaType.TextPlain), Some("4")) shouldBe None

    // a zero-length body prefers the empty variant, whatever content type was declared
    optionalProfilePicture.variantForDecode(Some(MediaType.ApplicationJson), Some("0")).get shouldBe a[EndpointIO.Empty[_]]
    optionalProfilePicture.variantForDecode(Some(MediaType.ImagePng), Some("0")).get shouldBe a[EndpointIO.Empty[_]]
    optionalProfilePicture.variantForDecode(Some(MediaType.TextPlain), Some("0")).get shouldBe a[EndpointIO.Empty[_]]
  }

  it should "keep using the only body variant when a body is present, whatever its content type" in {
    val body = optionalString.variantForDecode(Some(MediaType.ApplicationJson), None)
    body.get shouldBe a[EndpointIO.Body[_, _]]
    optionalString.variantForDecode(None, None).get shouldBe a[EndpointIO.Empty[_]]
    optionalString.variantForDecode(None, Some("0")).get shouldBe a[EndpointIO.Empty[_]]
    optionalString.variantForDecode(Some(MediaType.TextPlain), Some("0")).get shouldBe a[EndpointIO.Empty[_]]
    optionalString.variantForDecode(None, Some("5")).get shouldBe a[EndpointIO.Body[_, _]]
  }
}
