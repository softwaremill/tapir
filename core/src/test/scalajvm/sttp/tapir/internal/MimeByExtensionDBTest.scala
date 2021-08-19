package sttp.tapir.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.MediaType

class MimeByExtensionDBTest extends AnyFlatSpec with Matchers {
  it should "correctly find mime types for extensions" in {
    MimeByExtensionDB("txt") shouldBe Some(MediaType.TextPlain)
    MimeByExtensionDB("html") shouldBe Some(MediaType("text", "html"))
    MimeByExtensionDB("css") shouldBe Some(MediaType("text", "css", Some("UTF-8")))
    MimeByExtensionDB("unknown") shouldBe None
  }
}
