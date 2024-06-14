package sttp.tapir.codec.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean._
import eu.timepit.refined.collection.{MaxSize, MinSize, NonEmpty}
import eu.timepit.refined.numeric.{Greater, GreaterEqual, Interval, Less, LessEqual}
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.W
import eu.timepit.refined.refineMV
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{DecodeResult, Schema, ValidationError, Validator}

import scala.annotation.nowarn

class TapirCodecRefinedTestScala2 extends AnyFlatSpec with Matchers with TapirCodecRefined {

  val nonEmptyStringCodec: PlainCodec[NonEmptyString] = implicitly[PlainCodec[NonEmptyString]]

  "Generated codec" should "correctly delegate to raw parser and refine it" in {
    nonEmptyStringCodec.decode("vive le fromage") shouldBe DecodeResult.Value(refineMV[NonEmpty]("vive le fromage"))
  }

  "Generated codec for MatchesRegex" should "use tapir Validator.Pattern" in {
    type VariableConstraint = MatchesRegex[W.`"[a-zA-Z][-a-zA-Z0-9_]*"`.T]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.pattern("[a-zA-Z][-a-zA-Z0-9_]*")
    identifierCodec.decode("-bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "-bad", _, _))) if validator == expectedValidator =>
    }
  }

  it should "decode value matching pattern" in {
    type VariableConstraint = MatchesRegex[W.`"[a-zA-Z][-a-zA-Z0-9_]*"`.T]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]
    identifierCodec.decode("ok") shouldBe DecodeResult.Value(refineMV[VariableConstraint]("ok"))
  }

  "Generated codec for MaxSize on string" should "use tapir Validator.maxLength" in {
    type VariableConstraint = MaxSize[W.`2`.T]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.maxLength(2)
    identifierCodec.decode("bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "bad", _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for MinSize on string" should "use tapir Validator.minLength" in {
    type VariableConstraint = MinSize[W.`42`.T]
    type VariableString = String Refined VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.minLength(42)
    identifierCodec.decode("bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "bad", _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for Less" should "use tapir Validator.max" in {
    type IntConstraint = Less[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for LessEqual" should "use tapir Validator.max" in {
    type IntConstraint = LessEqual[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3)
    limitedIntCodec.decode("4") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 4, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for GreaterEqual" should "use tapir Validator.min" in {
    type IntConstraint = GreaterEqual[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3)
    limitedIntCodec.decode("2") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 2, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated validator for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[W.`3`.T]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Min(3, true), _) =>
    }
  }

  "Generated validator for Interval.Open" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.Open[W.`1`.T, W.`3`.T]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, true), Validator.Max(3, true))), _) =>
    }
  }

  "Generated validator for Interval.Close" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.Closed[W.`1`.T, W.`3`.T]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, false), Validator.Max(3, false))), _) =>
    }
  }

  "Generated validator for Interval.OpenClose" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.OpenClosed[W.`1`.T, W.`3`.T]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, true), Validator.Max(3, false))), _) =>
    }
  }

  "Generated validator for Interval.ClosedOpen" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.ClosedOpen[W.`1`.T, W.`3`.T]
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, false), Validator.Max(3, true))), _) =>
    }
  }

  "Generate validator for Or" should "use tapir Validator.any" in {
    type IntConstraint = Greater[W.`3`.T] Or Less[W.` -3`.T]
    type LimitedInt = Int Refined IntConstraint
    implicitly[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.Any(List(Validator.Min(3, true), Validator.Max(-3, true))), _) =>
    }
  }

  "Generated schema for NonEmpty and MinSize" should "not be optional" in {
    assert(implicitly[Schema[List[Int]]].isOptional)
    assert(!implicitly[Schema[List[Int] Refined NonEmpty]].isOptional)
    assert(!implicitly[Schema[Set[Int] Refined NonEmpty]].isOptional)
    assert(!implicitly[Schema[List[Int] Refined MinSize[W.`3`.T]]].isOptional)
    assert(!implicitly[Schema[List[Int] Refined (MinSize[W.`3`.T] And MaxSize[W.`6`.T])]].isOptional)
    assert(implicitly[Schema[List[Int] Refined MinSize[W.`0`.T]]].isOptional)
    assert(implicitly[Schema[List[Int] Refined MaxSize[W.`5`.T]]].isOptional)
    assert(implicitly[Schema[Option[List[Int] Refined NonEmpty]]].isOptional)
  }

  "TapirCodecRefined" should "compile using implicit schema for refined types" in {
    import io.circe.refined._
    import sttp.tapir
    import sttp.tapir._
    import sttp.tapir.json.circe._

    @nowarn // we only want to ensure it compiles but it warns because it is not used
    object TapirCodecRefinedDeepImplicitSearch extends TapirCodecRefined with TapirJsonCirce {
      type StringConstraint = MatchesRegex[W.`"[^\u0000-\u001f]{1,29}"`.T]
      type LimitedString = String Refined StringConstraint

      val refinedEndpoint: PublicEndpoint[(LimitedString, List[LimitedString]), Unit, List[Option[LimitedString]], Nothing] =
        tapir.endpoint.post
          .in(path[LimitedString]("ls") / jsonBody[List[LimitedString]])
          .out(jsonBody[List[Option[LimitedString]]])
    }
  }

}
