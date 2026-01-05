package sttp.tapir.json.tethys

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.generic.auto._
import tethys.derivation.auto._
import tethys.readers.ReaderError

object TapirJsonTethysCodec extends TapirJsonTethys

class TapirJsonTethysTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  it should "return a JSON specific decode error on failure" in {
    val codec = TapirJsonTethysCodec.tethysCodec[Customer]

    val actual = codec.decode("{}")
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual "{}"
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual List.empty
    error.underlying shouldBe a[ReaderError]
  }

}
