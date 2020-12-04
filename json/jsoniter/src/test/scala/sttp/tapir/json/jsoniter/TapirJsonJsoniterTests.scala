package sttp.tapir.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult

object TapirJsoniterCodec extends TapirJsonJsoniter

class TapirJsonJsoniterTests extends AnyFlatSpecLike with Matchers {

  it should "return a JSON specific decode error on failure" in {
    implicit val codec: JsonValueCodec[String] = JsonCodecMaker.make
    val tapirCodec = TapirJsoniterCodec.jsoniterCodec[String]
    val actual = tapirCodec.decode("[]")
    actual shouldBe a[DecodeResult.InvalidJson]
    actual.asInstanceOf[DecodeResult.InvalidJson].json shouldEqual "[]"
  }
}
