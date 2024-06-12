package sttp.tapir.codec.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.Or
import eu.timepit.refined.collection.{MaxSize, MinSize, NonEmpty}
import eu.timepit.refined.numeric.{Greater, GreaterEqual, Interval, Less, LessEqual}
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.refineV
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{DecodeResult, Schema, ValidationError, Validator}

class TapirCodecRefinedTestScala3 extends AnyFlatSpec with Matchers with TapirCodecRefined {

  val nonEmptyStringCodec: PlainCodec[NonEmptyString] = implicitly[PlainCodec[NonEmptyString]]

  "Generated codec" should "correctly delegate to raw parser and refine it" in {
    refineV[NonEmpty]("vive le fromage") match {
      case Right(nes) => nonEmptyStringCodec.decode("vive le fromage") shouldBe DecodeResult.Value(nes)
      case Left(_)    => fail()
    }
  }

  "Generated codec for MatchesRegex" should "use tapir Validator.Pattern" in {
    type VariableConstraint = MatchesRegex["[a-zA-Z][-a-zA-Z0-9_]*"]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.pattern("[a-zA-Z][-a-zA-Z0-9_]*")
    identifierCodec.decode("-bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "-bad", _, _))) if validator == expectedValidator =>
    }
  }

  it should "decode value matching pattern" in {
    type VariableConstraint = MatchesRegex["[a-zA-Z][-a-zA-Z0-9_]*"]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]
    refineV[VariableConstraint]("ok") match {
      case Right(s) => identifierCodec.decode("ok") shouldBe DecodeResult.Value(s)
      case Left(_)  => fail()
    }
  }

  "Generated codec for MaxSize on string" should "use tapir Validator.maxLength" in {
    type VariableConstraint = MaxSize[2]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.maxLength(2)
    identifierCodec.decode("bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "bad", _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for MinSize on string" should "use tapir Validator.minLength" in {
    type VariableConstraint = MinSize[42]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.minLength(42)
    identifierCodec.decode("bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "bad", _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for Less" should "use tapir Validator.max" in {
    type IntConstraint = Less[3]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for LessEqual" should "use tapir Validator.max" in {
    type IntConstraint = LessEqual[3]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3)
    limitedIntCodec.decode("4") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 4, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[3]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for GreaterEqual" should "use tapir Validator.min" in {
    type IntConstraint = GreaterEqual[3]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3)
    limitedIntCodec.decode("2") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 2, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated validator for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[3]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Min(3, true), _) =>
    }
  }

  "Generated validator for Interval.Open" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.Open[1, 3]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, true), Validator.Max(3, true))), _) =>
    }
  }

  "Generated validator for Interval.Close" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.Closed[1, 3]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, false), Validator.Max(3, false))), _) =>
    }
  }

  "Generated validator for Interval.OpenClose" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.OpenClosed[1, 3]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, true), Validator.Max(3, false))), _) =>
    }
  }

  "Generated validator for Interval.ClosedOpen" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.ClosedOpen[1, 3]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, false), Validator.Max(3, true))), _) =>
    }
  }

  "Generate validator for Or" should "use tapir Validator.any" in {
    type IntConstraint = Greater[3] Or Less[-3]
    type LimitedInt = Int Refined IntConstraint
    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.Any(List(Validator.Min(3, true), Validator.Max(-3, true))), _) =>
    }
  }

  "TapirCodecRefined" should "compile using implicit schema for refined types" in {
    import io.circe.refined._
    import sttp.tapir
    import sttp.tapir._
    import sttp.tapir.json.circe._

    object TapirCodecRefinedDeepImplicitSearch extends TapirCodecRefined with TapirJsonCirce {
      type StringConstraint = MatchesRegex["[^\u0000-\u001f]{1,29}"]
      type LimitedString = String Refined StringConstraint

      val refinedEndpoint: PublicEndpoint[(LimitedString, List[LimitedString]), Unit, List[Option[LimitedString]], Nothing] =
        tapir.endpoint.post
          .in(path[LimitedString]("ls") / jsonBody[List[LimitedString]])
          .out(jsonBody[List[Option[LimitedString]]])
    }
  }

}
