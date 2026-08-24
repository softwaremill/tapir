package sttp.tapir.client.sttp4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.Request
import sttp.model.Uri._
import sttp.tapir._

class ExtractedBodyClientTest extends AnyFlatSpec with Matchers {
  it should "send only the primary body, ignoring the extracted one" in {
    // the extracted body is on `in`, processed after `securityIn` - so an unskipped one would overwrite the primary
    val e = endpoint.post
      .in("people")
      .securityIn(stringBody)
      .in(extractBodyFromRequest(stringBody))
      .out(stringBody)

    val request: Request[_] =
      SttpClientInterpreter().toSecureRequestThrowDecodeFailures(e, Some(uri"http://example.com"))("sent")("ignored")

    request.body.show should include("sent")
    request.body.show should not include ("ignored")
  }
}
