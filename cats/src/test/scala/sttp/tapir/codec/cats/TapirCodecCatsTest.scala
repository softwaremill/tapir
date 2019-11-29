package sttp.tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.SchemaType.{SArray, SString}
import sttp.tapir.{Schema, Validator}

class TapirCodecCatsTest extends AnyFlatSpec with Matchers {
  case class Test(value: String)

  implicit val validatorForTest: Validator[Test] = Validator.minLength(3).contramap(_.value)

  it should "find schema for cats collections" in {
    implicitly[Schema[NonEmptyList[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptyList[String]]].isOptional shouldBe false

    implicitly[Schema[NonEmptySet[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptySet[String]]].isOptional shouldBe false

    implicitly[Schema[NonEmptyChain[String]]].schemaType shouldBe SArray(Schema(SString))
    implicitly[Schema[NonEmptyChain[String]]].isOptional shouldBe false
  }

  it should "find proper validator for cats collections" in {
    val expectedValidator = validatorForTest.asIterableElements[List].and(Validator.minSize(1))

    implicitly[Validator[NonEmptyList[Test]]].show shouldBe expectedValidator.show

    implicitly[Validator[NonEmptySet[Test]]].show shouldBe expectedValidator.show

    implicitly[Validator[NonEmptyChain[Test]]].show shouldBe expectedValidator.show
  }
}
