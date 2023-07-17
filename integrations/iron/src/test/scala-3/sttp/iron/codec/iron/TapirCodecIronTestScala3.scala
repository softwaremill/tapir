package sttp.iron.codec.iron

import io.github.iltotore.iron.*

import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.Codec
import sttp.tapir.Schema

import sttp.tapir.codec.iron.given
import sttp.tapir.codec.iron.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import io.github.iltotore.iron.constraint.all.*
import sttp.tapir.Validator
import sttp.tapir.ValidationError

class TapirCodecIronTestScala3 extends AnyFlatSpec with Matchers {

  val schema: Schema[Double :| Positive] = summon[Schema[Double :| Positive]]

  val codec: Codec[String, Double :| Positive, TextPlain] =
    summon[Codec[String, Double :| Positive, TextPlain]]

  "Generated codec" should "correctly delegate to raw parser and refine it" in {
    10.refineEither[Positive] match {
      case Right(nes) => codec.decode("10") shouldBe DecodeResult.Value(nes)
      case Left(_)    => fail()
    }
  }

  // "Generated codec for MatchesRegex" should "use tapir Validator.Pattern" in {
  //   type VariableConstraint = Match["[a-zA-Z][-a-zA-Z0-9_]*"]
  //   type VariableString = String :| VariableConstraint
  //   val identifierCodec = implicitly[PlainCodec[VariableString]]

  //   val expectedValidator: Validator[String] = Validator.pattern("[a-zA-Z][-a-zA-Z0-9_]*")

  //   // identifierCodec.decode("-bad") match {
  //   //   case DecodeResult.InvalidValue(List(ValidationError(validator, "-bad", _, _))) => println(validator)
  //   // }

  //   identifierCodec.decode("-bad") should matchPattern {
  //     case DecodeResult.InvalidValue(List(ValidationError(validator, "-bad", _, _))) if validator == expectedValidator =>
  //   }
  // }

}
