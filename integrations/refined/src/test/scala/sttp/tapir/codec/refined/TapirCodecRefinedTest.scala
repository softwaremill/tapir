package sttp.tapir.codec.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.{Greater, GreaterEqual, Less, LessEqual}
import eu.timepit.refined.string.{IPv4, MatchesRegex}
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.{W, refineMV, refineV}
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{DecodeResult, ValidationError, Validator}

class TapirCodecRefinedTest extends FlatSpec with Matchers with TapirCodecRefined {

  val nonEmptyStringCodec: PlainCodec[NonEmptyString] = implicitly[PlainCodec[NonEmptyString]]

  "Generated codec" should "return DecodResult.Invalid if subtype can't be refined with correct tapir validator if available" in {
    val expectedValidator: Validator[String] = Validator.minLength(1)
    nonEmptyStringCodec.decode("") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "", _))) if validator == expectedValidator =>
    }
  }

  it should "correctly delegate to raw parser and refine it" in {
    nonEmptyStringCodec.decode("vive le fromage") shouldBe DecodeResult.Value(refineMV[NonEmpty]("vive le fromage"))
  }

  it should "return DecodResult.Invalid if subtype can't be refined with derived tapir validator if non tapir validator available" in {
    type IPString = String Refined IPv4
    val IPStringCodec = implicitly[PlainCodec[IPString]]

    val expectedMsg = refineV[IPv4]("192.168.0.1000").left.get
    IPStringCodec.decode("192.168.0.1000") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(Validator.Custom(_, `expectedMsg`), "192.168.0.1000", _))) =>
    }
  }

  "Generated codec for MatchesRegex" should "use tapir Validator.Pattern" in {
    type VariableConstraint = MatchesRegex[W.`"[a-zA-Z][-a-zA-Z0-9_]*"`.T]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.pattern("[a-zA-Z][-a-zA-Z0-9_]*")
    identifierCodec.decode("-bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "-bad", _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for Less" should "use tapir Validator.max" in {
    type IntConstraint = Less[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for LessEqual" should "use tapir Validator.max" in {
    type IntConstraint = LessEqual[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3)
    limitedIntCodec.decode("4") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 4, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for GreaterEqual" should "use tapir Validator.min" in {
    type IntConstraint = GreaterEqual[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3)
    limitedIntCodec.decode("2") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 2, _))) if validator == expectedValidator =>
    }
  }

  "Generated validator for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Validator[LimitedInt]] should matchPattern {
      case Validator.Mapped(Validator.Min(3, true), _) =>
    }
  }
}
