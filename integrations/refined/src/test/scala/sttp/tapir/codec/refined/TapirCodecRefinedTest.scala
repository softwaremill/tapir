package sttp.tapir.codec.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean._
import eu.timepit.refined.collection._
import eu.timepit.refined.numeric.{Negative, NonNegative, NonPositive, Positive}
import eu.timepit.refined.string.IPv4
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.refineV
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

  it should "return DecodeResult.Invalid if subtype can't be refined with derived tapir validator if non tapir validator available" in {
    type IPString = String Refined IPv4
    val IPStringCodec = implicitly[PlainCodec[IPString]]

    val expectedMsg = refineV[IPv4]("192.168.0.1000").swap.getOrElse(throw new Exception("A Left was expected but got a Right"))
    IPStringCodec.decode("192.168.0.1000") should matchPattern {
      case DecodeResult.InvalidValue(List(ValidationError(_, "192.168.0.1000", _, Some(`expectedMsg`)))) =>
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

  "Generated schema for NonEmpty and MinSize" should "not be optional" in {    
    assert(implicitly[Schema[List[Int]]].isOptional)
    assert(!implicitly[Schema[List[Int] Refined NonEmpty]].isOptional)
    assert(!implicitly[Schema[Set[Int] Refined NonEmpty]].isOptional)
    assert(!implicitly[Schema[List[Int] Refined MinSize[3]]].isOptional)
    assert(!implicitly[Schema[List[Int] Refined (MinSize[3] And MaxSize[6])]].isOptional)
    assert(implicitly[Schema[List[Int] Refined MinSize[0]]].isOptional)
    assert(implicitly[Schema[List[Int] Refined MaxSize[5]]].isOptional)
    assert(implicitly[Schema[Option[List[Int] Refined NonEmpty]]].isOptional)
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
