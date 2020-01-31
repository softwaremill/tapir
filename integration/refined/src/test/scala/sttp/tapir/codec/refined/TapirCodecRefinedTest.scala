package sttp.tapir.codec.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.{IPv4, MatchesRegex}
import eu.timepit.refined.{W, refineMV, refineV}
import eu.timepit.refined.types.string.NonEmptyString
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{DecodeResult, ValidationError, Validator}

class TapirCodecRefinedTest extends FlatSpec with Matchers with TapirCodecRefined {

  val nonEmptyStringCodec = implicitly[PlainCodec[NonEmptyString]]


  "Generated codec" should "return DecodResult.Invalid if subtype can't be refined with correct tapir validator if available" in {
    val expectedValidator: Validator[String] = Validator.minLength(1)
    nonEmptyStringCodec.decode("") should matchPattern{case DecodeResult.InvalidValue(List(ValidationError(validator, "", _))) if validator == expectedValidator=>}
  }

  it should "correctly delegate to raw parser and refine it" in {
    nonEmptyStringCodec.decode("vive le fromage") shouldBe DecodeResult.Value(refineMV[NonEmpty]("vive le fromage"))
  }

  it should "return DecodResult.Invalid if subtype can't be refined with derived tapir validator if non tapir validator available" in {
    type IPString = String Refined IPv4
    val IPStringCodec = implicitly[PlainCodec[IPString]]

    val expectedMsg = refineV[IPv4]("192.168.0.1000").left.get
    IPStringCodec.decode("192.168.0.1000") should matchPattern{case DecodeResult.InvalidValue(List(ValidationError(Validator.Custom(_, `expectedMsg`), "192.168.0.1000", _)))=>}
  }

  "Generated codec for MatchesRegex" should "use tapir Validator.Pattern" in {
    type VariableConstraint = MatchesRegex[W.`"[a-zA-Z][-a-zA-Z0-9_]*"`.T]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.pattern("[a-zA-Z][-a-zA-Z0-9_]*")
    identifierCodec.decode("-bad") should matchPattern{case DecodeResult.InvalidValue(List(ValidationError(validator, "-bad", _))) if validator == expectedValidator=>}
  }
}
