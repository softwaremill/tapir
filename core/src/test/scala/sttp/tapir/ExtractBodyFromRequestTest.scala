package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExtractBodyFromRequestTest extends AnyFlatSpec with Matchers {
  it should "mark a string body as extracted" in {
    extractBodyFromRequest(stringBody).attribute(ExtractedBody.attributeKey) shouldBe Some(ExtractedBody())
  }

  it should "mark a json-style string body as extracted" in {
    val body = stringBodyUtf8AnyFormat(Codec.string)
    extractBodyFromRequest(body).attribute(ExtractedBody.attributeKey) shouldBe Some(ExtractedBody())
  }

  it should "leave a plain body unmarked" in {
    stringBody.attribute(ExtractedBody.attributeKey) shouldBe None
  }

  it should "preserve the codec and body type" in {
    val extracted = extractBodyFromRequest(byteArrayBody)
    extracted.bodyType shouldBe RawBodyType.ByteArrayBody
    extracted.codec shouldBe byteArrayBody.codec
  }

  it should "not compile for file bodies" in {
    assertDoesNotCompile("extractBodyFromRequest(fileBody)")
  }

  it should "not compile for multipart bodies" in {
    assertDoesNotCompile("extractBodyFromRequest(multipartBody)")
  }

  it should "not compile for oneOfBody" in {
    assertDoesNotCompile("""extractBodyFromRequest(oneOfBody(stringBody, stringBody))""")
  }
}
