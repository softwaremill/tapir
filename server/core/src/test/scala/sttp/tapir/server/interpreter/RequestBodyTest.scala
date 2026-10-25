package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.{Header, HeaderNames}
import sttp.tapir.server.TestUtil.createTestRequest

import java.nio.charset.StandardCharsets

class RequestBodyTest extends AnyFlatSpec with Matchers {
  private def requestCharset(contentType: Option[String]) =
    RequestBody.requestCharset(createTestRequest(Nil, _headers = contentType.map(Header(HeaderNames.ContentType, _)).toList))

  it should "take the charset from the Content-Type header" in {
    requestCharset(Some("text/plain; charset=ISO-8859-1")) shouldBe Some(StandardCharsets.ISO_8859_1)
  }

  it should "give no charset when the Content-Type header has none, or there's no such header" in {
    requestCharset(Some("text/plain")) shouldBe None
    requestCharset(None) shouldBe None
  }

  it should "give no charset when it's invalid or not supported" in {
    requestCharset(Some("text/plain; charset=no-such-charset")) shouldBe None
    requestCharset(Some("text/plain; charset=%")) shouldBe None
  }
}
