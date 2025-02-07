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

import com.example.RefinedString
import com.example.RefinedStringConstraint

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

  "Generated codec for MatchesRegex" should "use tapir Validator.Pattern" in {
    type VariableConstraint = Match["[a-zA-Z][-a-zA-Z0-9_]*"]
    type VariableString = String :| VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.pattern("[a-zA-Z][-a-zA-Z0-9_]*")

    identifierCodec.decode("-bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "-bad", _, _))) if validator == expectedValidator =>
    }
  }

  it should "decode value matching pattern" in {
    type VariableConstraint = Match["[a-zA-Z][-a-zA-Z0-9_]*"]
    type VariableString = String :| VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]
    "ok".refineEither[VariableConstraint] match {
      case Right(s) => identifierCodec.decode("ok") shouldBe DecodeResult.Value(s)
      case Left(_)  => fail()
    }
  }

  "Generated codec for MaxLength on string" should "use tapir Validator.maxLength" in {
    type VariableConstraint = MaxLength[2]
    type VariableString = String :| VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.maxLength(2)
    identifierCodec.decode("bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "bad", _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for MinLength on string" should "use tapir Validator.minLength" in {
    type VariableConstraint = MinLength[42]
    type VariableString = String :| VariableConstraint
    val identifierCodec = implicitly[PlainCodec[VariableString]]

    val expectedValidator: Validator[String] = Validator.minLength(42)
    identifierCodec.decode("bad") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, "bad", _, _))) if validator == expectedValidator =>
    }
  }

  "Generated schema for described Int with extra constrains" should "apply given constrains" in {
    val schema = implicitly[Schema[Int :| (Positive DescribedAs "Age should be positive")]]

    schema.validator should matchPattern { case Validator.Mapped(Validator.Min(0, true), _) =>
    }
  }

  "Generated schema for described String with extra constrains" should "apply given constrains" in {
    type Constraint = (Not[Empty] & Alphanumeric) DescribedAs "name should not be empty and only made of alphanumeric characters"
    type VariableString = String :| Constraint
    val schema = implicitly[Schema[VariableString]]

    schema.validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.MinLength(1, false), Validator.Custom(_, _))), _) =>
    }
    val codec = implicitly[PlainCodec[VariableString]]
    codec.decode("alpha1") shouldBe a[DecodeResult.Value[_]]
    codec.decode("bad!") shouldBe a[DecodeResult.InvalidValue]
    codec.decode("") shouldBe a[DecodeResult.InvalidValue]
    codec.decode("954") shouldBe a[DecodeResult.Value[_]]
    codec.decode("spaces not allowed") shouldBe a[DecodeResult.InvalidValue]
  }

  "Generated schema for non empty string" should "use a MinLength validator" in {
    type VariableString = String :| Not[Empty]
    val schema = implicitly[Schema[VariableString]]

    schema.validator should matchPattern { case Validator.Mapped(Validator.MinLength(1, false), _) =>
    }
  }

  "Generated schema for union and intersection on string" should "use a list of tapir validations" in {
    type VariableString = String :| (MinLength[33] & MaxLength[83])
    val schema = implicitly[Schema[VariableString]]

    schema.validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.MinLength(33, false), Validator.MaxLength(83, false))), _) =>
    }
  }

  "Generated codec for Less" should "use tapir Validator.max" in {
    type IntConstraint = Less[3]
    type LimitedInt = Int :| IntConstraint
    val limitedIntCodec = implicitly[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for LessEqual" should "use tapir Validator.max" in {
    type IntConstraint = LessEqual[3]
    type LimitedInt = Int :| IntConstraint
    val limitedIntCodec = summon[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.max(3)
    limitedIntCodec.decode("4") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 4, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[3]
    type LimitedInt = Int :| IntConstraint
    val limitedIntCodec = summon[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3, exclusive = true)
    limitedIntCodec.decode("3") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 3, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated codec for GreaterEqual" should "use tapir Validator.min" in {
    type IntConstraint = GreaterEqual[3]
    type LimitedInt = Int :| IntConstraint
    val limitedIntCodec = summon[PlainCodec[LimitedInt]]

    val expectedValidator: Validator[Int] = Validator.min(3)
    limitedIntCodec.decode("2") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(validator, 2, _, _))) if validator == expectedValidator =>
    }
  }

  "Generated validator for Greater" should "use tapir Validator.min" in {
    type IntConstraint = Greater[3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Min(3, true), _) =>
    }
  }

  "Generated validator for Interval.Open" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.Open[1, 3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, true), Validator.Max(3, true))), _) =>
    }
  }

  "Generated validator for Interval.Close" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.Closed[1, 3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, false), Validator.Max(3, false))), _) =>
    }
  }

  "Generated validator for Interval.OpenClose" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.OpenClosed[1, 3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, true), Validator.Max(3, false))), _) =>
    }
  }

  "Generated validator for Interval.ClosedOpen" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Interval.ClosedOpen[1, 3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, false), Validator.Max(3, true))), _) =>
    }
  }

  "Generated validator for intersection of constraints" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Greater[1] & Less[3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, true), Validator.Max(3, true))), _) =>
    }
  }
  "Generated validator for intersection of constraints" should "use tapir Validator.min(1, false) and Validator.max(3, false)" in {
    type IntConstraint = GreaterEqual[1] & LessEqual[3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.All(List(Validator.Min(1, false), Validator.Max(3, false))), _) =>
    }
  }

  "Generated validator for union of constraints" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = Less[1] | Greater[3]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.Any(List(Validator.Max(1, true), Validator.Min(3, true))), _) =>
    }
  }

  "Generated validator for union of constraints" should "use tapir Validator.min and strict equality (enumeration)" in {
    type IntConstraint = StrictEqual[3] | Greater[5]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.Any(List(Validator.Enumeration(List(3), _, _), Validator.Min(5, true))), _) =>
    }
  }

  "Generated validator for union of constraints" should "put muiltiple StrictEquality into a single enum and follow with the rest of constrains" in {
    type IntConstraint = StrictEqual[3] | StrictEqual[4] | StrictEqual[13] | GreaterEqual[23]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.Any(List(Validator.Enumeration(List(3, 4, 13), _, _), Validator.Min(23, false))), _) =>
    }
  }

  "Generated validator for union of constraints" should "use tapir Validator.enumeration" in {
    type IntConstraint = In[
      (
          110354433,
          110354454,
          122483323
      )
    ]
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(
            Validator.Enumeration(
              List(
                110354433,
                110354454,
                122483323
              ),
              _,
              _
            ),
            _
          ) =>
    }
  }

  "Generated validator for described union" should "use tapir Validator.min and Validator.max" in {
    type IntConstraint = (Less[1] | Greater[3]) DescribedAs ("Should be included in less than 1 or more than 3")
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern {
      case Validator.Mapped(Validator.Any(List(Validator.Max(1, true), Validator.Min(3, true))), _) =>
    }
  }

  "Generated validator for described union" should "work with strings" in {
    type StrConstraint = (Match["[a-c]*"] | Match["[x-z]*"]) DescribedAs ("Some description")
    type LimitedStr = String :| StrConstraint

    val identifierCodec = implicitly[PlainCodec[LimitedStr]]
    identifierCodec.decode("aac") shouldBe DecodeResult.Value("aac")
    identifierCodec.decode("yzx") shouldBe DecodeResult.Value("yzx")
    identifierCodec.decode("aax") shouldBe a[DecodeResult.InvalidValue]
  }

  "Generated validator for described single constraint" should "use tapir Validator.max" in {
    type IntConstraint = (Less[1]) DescribedAs ("Should be included in less than 1 or more than 3")
    type LimitedInt = Int :| IntConstraint

    summon[Schema[LimitedInt]].validator should matchPattern { case Validator.Mapped(Validator.Max(1, true), _) =>
    }
  }

  "Generated schema for NonEmpty and MinSize" should "not be optional" in {
    assert(implicitly[Schema[List[Int]]].isOptional)
    assert(!implicitly[Schema[List[Int] :| Not[Empty]]].isOptional)
    assert(!implicitly[Schema[Set[Int] :| Not[Empty]]].isOptional)
    assert(!implicitly[Schema[List[Int] :| MinLength[3]]].isOptional)
    assert(!implicitly[Schema[List[Int] :| (MinLength[3] & MaxLength[6])]].isOptional)
    assert(implicitly[Schema[List[Int] :| MinLength[0]]].isOptional)
    assert(implicitly[Schema[List[Int] :| MaxLength[5]]].isOptional)
    assert(implicitly[Schema[Option[List[Int] :| Not[Empty]]]].isOptional)
  }

  "Instances for opaque refined type" should "be correctly derived" in:
    summon[Schema[RefinedInt]]
    summon[Codec[String, RefinedInt, TextPlain]]

  "Instances for newtypes" should "be correctly derived" in:
    type NewtypeInt = Int :| Pure
    summon[Schema[NewtypeInt]]
    summon[Codec[String, NewtypeInt, TextPlain]]

  "Instances for opaque refined type defined outside of source" should "be correctly derived" in:
    summon[Schema[RefinedString]]
    summon[Codec[String, RefinedString, TextPlain]]

  "Instance of validator for constraint defined outside of source" should "be correctly derived" in:
    summon[PrimitiveValidatorForPredicate[String, RefinedStringConstraint]]

}
