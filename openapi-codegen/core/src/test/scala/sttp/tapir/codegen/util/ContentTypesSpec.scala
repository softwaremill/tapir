package sttp.tapir.codegen.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ContentTypesSpec extends AnyFlatSpec with Matchers {
  it should "recognise json content types" in {
    Seq("application/json", "application/problem+json", "application/vnd.example.v1+json").foreach(ContentTypes.isJson(_) shouldBe true)
    Seq("application/jsonx", "text/json", "application/+json", "application/json+xml", "application/xml").foreach(
      ContentTypes.isJson(_) shouldBe false
    )
  }

  it should "recognise xml content types" in {
    Seq("application/xml", "application/problem+xml", "application/atom+xml").foreach(ContentTypes.isXml(_) shouldBe true)
    Seq("application/xmlx", "text/xml", "application/+xml", "application/json").foreach(ContentTypes.isXml(_) shouldBe false)
  }

  it should "treat only non-plain json and xml types as suffixed" in {
    Seq("application/problem+json", "application/atom+xml").foreach(ContentTypes.isSuffixed(_) shouldBe true)
    Seq("application/json", "application/xml", "text/plain").foreach(ContentTypes.isSuffixed(_) shouldBe false)
  }
}
