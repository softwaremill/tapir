package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.MediaType

class OneOfInputTest extends AnyFlatSpec with Matchers {
  private val optionalString = oneOfInput[Option[String]](
    oneOfVariantValueMatcher(emptyInputAs((None: Option[String]))) { case None => true },
    oneOfVariantValueMatcher(stringBody.map(Some(_))(_.get)) { case Some(_) => true }
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
    oneOfVariantValueMatcher(jsonBody) { case _: LinkToExistingImage => true },
    oneOfVariantValueMatcher(pngBody) { case _: PngImage => true }
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

  it should "reject an output-only variant" in {
    assertThrows[IllegalArgumentException] {
      oneOfInput[String](oneOfVariant(statusCode(sttp.model.StatusCode.Ok).map(_ => "ok")(_ => ())))
    }
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
  }

  it should "keep using the only body variant when a body is present, whatever its content type" in {
    val body = optionalString.variantForDecode(Some(MediaType.ApplicationJson), None)
    body.get shouldBe a[EndpointIO.Body[_, _]]
    optionalString.variantForDecode(None, None).get shouldBe a[EndpointIO.Empty[_]]
    optionalString.variantForDecode(None, Some("0")).get shouldBe a[EndpointIO.Empty[_]]
    optionalString.variantForDecode(None, Some("5")).get shouldBe a[EndpointIO.Body[_, _]]
  }
}
