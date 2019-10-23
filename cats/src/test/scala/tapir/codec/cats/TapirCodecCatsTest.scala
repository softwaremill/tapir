package tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema.{SArray, SString}
import tapir.{SchemaFor, Validator}

class TapirCodecCatsTest extends FlatSpec with Matchers {

  case class Test(value: String)

  implicit val validatorForTest: Validator[Test] = Validator.minLength(3).contramap(_.value)

  it should "find schema for cats collections" in {
    implicitly[SchemaFor[NonEmptyList[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[NonEmptyList[String]]].isOptional shouldBe false

    implicitly[SchemaFor[NonEmptySet[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[NonEmptySet[String]]].isOptional shouldBe false

    implicitly[SchemaFor[NonEmptyChain[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[NonEmptyChain[String]]].isOptional shouldBe false
  }

  it should "find proper validator for cats collections" in {
    val expectedValidator = validatorForTest.asIterableElements[List].and(Validator.minSize(1))

    implicitly[Validator[NonEmptyList[Test]]].show shouldBe expectedValidator.show

    implicitly[Validator[NonEmptySet[Test]]].show shouldBe expectedValidator.show

    implicitly[Validator[NonEmptyChain[Test]]].show shouldBe expectedValidator.show
  }

}
