package sttp.tapir.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.generic.auto._

object TapirJsoniterCodec extends TapirJsonJsoniter

class TapirJsonJsoniterTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  it should "return a JSON specific decode error on failure" in {
    implicit val codec: JsonValueCodec[Customer] = JsonCodecMaker.make
    val tapirCodec = TapirJsoniterCodec.jsoniterCodec[Customer]
    val actual = tapirCodec.decode("{}")
    actual shouldBe a[DecodeResult.InvalidJson]
    val invalidJsonFailure = actual.asInstanceOf[DecodeResult.InvalidJson]
    invalidJsonFailure.json shouldEqual "{}"
    invalidJsonFailure.errors shouldEqual List.empty
    invalidJsonFailure.underlying shouldBe a[JsonReaderException]
  }

}
