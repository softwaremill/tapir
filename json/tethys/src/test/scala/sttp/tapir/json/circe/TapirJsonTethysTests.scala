package sttp.tapir.json.circe

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult

object TapirJsonTethysCodec extends TapirJsonTethys

class TapirJsonTethysTests extends AnyFlatSpecLike with Matchers {

  it should "return a JSON specific decode error on failure" in {
    val codec = TapirJsonTethysCodec.tethysCodec[String]
    val actual = codec.decode("[]")
    actual shouldBe a[DecodeResult.InvalidJson]
    actual.asInstanceOf[DecodeResult.InvalidJson].json shouldEqual "[]"
  }
}
