package sttp.tapir.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.MediaType
import sttp.tapir._

// TODO: move to shared test sources when https://github.com/softwaremill/sttp-model/issues/188 is fixed
class RichEndpointOutputTest extends AnyFlatSpec with Matchers {
  "output media type" should "match content type with lower and upper case charset" in {
    val o = endpoint.put
      .in("api" / path[String]("version"))
      .out(stringBody)
      .output

    o.hasOptionalBodyMatchingContent(MediaType.unsafeParse("text/plain; charset=utf-8")) should be(true)
    o.hasOptionalBodyMatchingContent(MediaType.unsafeParse("text/plain; charset=UTF-8")) should be(true)
  }
}
