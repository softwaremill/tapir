package sttp.tapir.json.circe

import io.circe.DecodingFailure
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.generic.auto._

class TapirJsonCirceTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  it should "return a JSON specific decode error on failure" in {
    val codec = circeCodec[Customer]
    val actual = codec.decode("{}")
    actual shouldBe a[DecodeResult.Error]
    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual "{}"
    failure.error shouldBe a[JsonDecodeException]
    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual
      List(JsonError("Attempt to decode value on failed cursor", Some(".name")))
    error.underlying shouldBe a[DecodingFailure]
  }

}
