package sttp.tapir

import org.scalatest.{Assertion, Inside}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.Value

import sttp.tapir.DecodeResult.InvalidValue

class CodecScala3Test extends AnyFlatSpec with Matchers with Checkers with Inside {

  it should "derive a codec for a string-based union type" in {
    // given
    val codec = summon[Codec[String, "Apple" | "Banana", TextPlain]]

    // then
    codec.encode("Apple") shouldBe "Apple"
    codec.encode("Banana") shouldBe "Banana"
    codec.decode("Apple") shouldBe Value("Apple")
    codec.decode("Banana") shouldBe Value("Banana")
    codec.decode("Orange") should matchPattern { case DecodeResult.InvalidValue(List(ValidationError(_, "Orange", _, _))) => }
  }
}
