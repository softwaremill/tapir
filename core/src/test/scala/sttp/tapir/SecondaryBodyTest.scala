package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.internal.SecondaryBody

class SecondaryBodyTest extends AnyFlatSpec with Matchers {
  it should "mark a string body as secondary" in {
    stringBody.asSecondary.attribute(SecondaryBody.Attribute) shouldBe Some(SecondaryBody())
  }

  it should "mark a json-style string body as secondary" in {
    val body = stringBodyUtf8AnyFormat(Codec.string)
    body.asSecondary.attribute(SecondaryBody.Attribute) shouldBe Some(SecondaryBody())
  }

  it should "leave a plain body unmarked" in {
    stringBody.attribute(SecondaryBody.Attribute) shouldBe None
    stringBody.isSecondary shouldBe false
  }

  it should "report a marked body through isSecondary" in {
    stringBody.asSecondary.isSecondary shouldBe true
  }

  it should "not treat a key built from the same name as a secondary marker" in {
    fileBody.attribute(new AttributeKey[Unit]("sttp.tapir.internal.SecondaryBody"), ()).isSecondary shouldBe false
  }

  it should "preserve the codec and body type" in {
    val secondary = byteArrayBody.asSecondary
    secondary.bodyType shouldBe RawBodyType.ByteArrayBody
    secondary.codec shouldBe byteArrayBody.codec
  }

  it should "not compile for file bodies" in {
    assertDoesNotCompile("fileBody.asSecondary")
  }

  it should "not compile for multipart bodies" in {
    assertDoesNotCompile("multipartBody.asSecondary")
  }

  it should "not compile for oneOfBody" in {
    assertDoesNotCompile("""oneOfBody(stringBody, stringBody).asSecondary""")
  }

  it should "render a secondary body distinctly in show" in {
    stringBody.asSecondary.show shouldBe "{secondary body as text/plain (UTF-8)}"
  }

  it should "render a plain body unchanged in show" in {
    stringBody.show shouldBe "{body as text/plain (UTF-8)}"
  }

  it should "report secondary bodies through the internal predicate" in {
    import sttp.tapir.internal._
    isSecondaryBodyInput(stringBody.asSecondary) shouldBe true
    isSecondaryBodyInput(stringBody) shouldBe false
    isSecondaryBodyInput(query[String]("q")) shouldBe false
  }
}
