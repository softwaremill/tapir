package sttp.tapir.codec.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.Or
import eu.timepit.refined.collection.{MaxSize, MinSize, NonEmpty}
import eu.timepit.refined.numeric.{Greater, GreaterEqual, Interval, Less, LessEqual, Negative, NonNegative, NonPositive, Positive}
import eu.timepit.refined.string.{IPv4, MatchesRegex}
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.{W, refineMV, refineV}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{DecodeResult, Schema, ValidationError, Validator}

class TapirCodecRefinedTest extends AnyFlatSpec with Matchers with TapirCodecRefined {

  val nonEmptyStringCodec: PlainCodec[NonEmptyString] = implicitly[PlainCodec[NonEmptyString]]

  "Generated codec" should "return DecodeResult.Invalid if subtype can't be refined with correct tapir validator if available" in {
    val expectedValidator: Validator[String] = Validator.minLength(1)
    nonEmptyStringCodec.decode("") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "", _, _))) if validator == expectedValidator =>
    }
  }

  it should "correctly delegate to raw parser and refine it" in {
    nonEmptyStringCodec.decode("vive le fromage") shouldBe DecodeResult.Value(refineMV[NonEmpty]("vive le fromage"))
  }

  it should "return DecodeResult.Invalid if subtype can't be refined with derived tapir validator if non tapir validator available" in {
    type IPString = String Refined IPv4
    val IPStringCodec = implicitly[PlainCodec[IPString]]

    val expectedMsg = refineV[IPv4]("192.168.0.1000").left.get
    IPStringCodec.decode("192.168.0.1000") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(_, "192.168.0.1000", _, Some(`expectedMsg`)))) =>
    }
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

  "Generated validator for Positive" should "use tapir Validator.min" in {
    type IntConstraint = Positive
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Min(0, true), _) => }
  }

  "Generated validator for NonNegative" should "use tapir Validator.min" in {
    type IntConstraint = NonNegative
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Min(0, false), _) => }
  }

  "Generated validator for NonPositive" should "use tapir Validator.max" in {
    type IntConstraint = NonPositive
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Max(0, false), _) => }
  }

  "Generated validator for Negative" should "use tapir Validator.max" in {
    type IntConstraint = Negative
    type LimitedInt = Int Refined IntConstraint

    implicitly[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Max(0, true), _) => }
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

  "TapirCodecRefined" should "compile using implicit schema for refined types" in {
    import io.circe.refined._
    import sttp.tapir
    import sttp.tapir._
    import sttp.tapir.json.circe._

    object TapirCodecRefinedDeepImplicitSearch extends TapirCodecRefined with TapirJsonCirce {
      type StringConstraint = MatchesRegex[W.`"[^\u0000-\u001f]{1,29}"`.T]
      type LimitedString = String Refined StringConstraint

      val refinedEndpoint: PublicEndpoint[(LimitedString, List[LimitedString]), Unit, List[Option[LimitedString]], Nothing] =
        tapir.endpoint.post
          .in(path[LimitedString]("ls") / jsonBody[List[LimitedString]])
          .out(jsonBody[List[Option[LimitedString]]])
    }
  }

  "Using refined" should "compile when using tapir endpoints" in {
    // this used to cause a:
    // [error] java.lang.StackOverflowError
    // [error] scala.reflect.internal.Types$TypeRef.foldOver(Types.scala:2376)
    // [error] scala.reflect.internal.tpe.TypeMaps$IsRelatableCollector$.apply(TypeMaps.scala:1272)
    // [error] scala.reflect.internal.tpe.TypeMaps$IsRelatableCollector$.apply(TypeMaps.scala:1267)
    import sttp.tapir._
    endpoint.in("x")
  }
}
