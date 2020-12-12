package sttp.tapir.json.circe

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.generic.auto._
import tethys.derivation.auto._
import tethys.readers.ReaderError

object TapirJsonTethysCodec extends TapirJsonTethys

class TapirJsonTethysTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  it should "return a JSON specific decode error on failure" in {
    val codec = TapirJsonTethysCodec.tethysCodec[Customer]
    val actual = codec.decode("{}")
    actual shouldBe a[DecodeResult.InvalidJson]
    val failure = actual.asInstanceOf[DecodeResult.InvalidJson]
    failure.json shouldEqual "{}"
    failure.errors shouldEqual List.empty
    failure.underlying shouldBe a[ReaderError]
  }

}
