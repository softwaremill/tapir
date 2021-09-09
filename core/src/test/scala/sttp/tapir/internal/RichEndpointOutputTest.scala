package sttp.tapir.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.MediaType
import sttp.tapir._

class RichEndpointOutputTest extends AnyFlatSpec with Matchers {
  "output media type" should "match content type with lower case charset" in {
    MediaType.parse("text/plain; charset=utf-8") match {
      case Right(mediaType) =>
        endpoint.put
          .in("api" / path[String]("version"))
          .out(stringBody)
          .output
          .hasBodyMatchingContent(mediaType) should be(true)

      case Left(error) => fail(s"unable to parse media type: $error")
    }
  }
}
