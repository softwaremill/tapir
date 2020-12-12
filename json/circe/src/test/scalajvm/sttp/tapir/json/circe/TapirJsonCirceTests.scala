package sttp.tapir.json.circe

import io.circe.DecodingFailure
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.generic.auto._

class TapirJsonCirceTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  it should "return a JSON specific decode error on failure" in {
    val codec = circeCodec[Customer]
    val actual = codec.decode("{}")
    actual shouldBe a[DecodeResult.InvalidJson]
    val invalidJsonFailure = actual.asInstanceOf[DecodeResult.InvalidJson]
    invalidJsonFailure.json shouldEqual "{}"
    invalidJsonFailure.errors shouldEqual List(
      DecodeResult.InvalidJson.Error(
        "Attempt to decode value on failed cursor",
        Some(".name")
      )
    )
    invalidJsonFailure.underlying shouldBe a[DecodingFailure]
  }

}
