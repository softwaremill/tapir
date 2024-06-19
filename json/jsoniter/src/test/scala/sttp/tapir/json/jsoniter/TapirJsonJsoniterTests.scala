package sttp.tapir.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.generic.auto._

object TapirJsoniterCodec extends TapirJsonJsoniter

class TapirJsonJsoniterTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  it should "return a JSON specific decode error on failure" in {
    implicit val codec: JsonValueCodec[Customer] = JsonCodecMaker.make
    val tapirCodec = TapirJsoniterCodec.jsoniterCodec[Customer]

    val actual = tapirCodec.decode("{}")
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual "{}"
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual List(JsonError("missing required field \"name\"", Nil))
    error.underlying shouldBe a[JsonReaderException]
  }

}
