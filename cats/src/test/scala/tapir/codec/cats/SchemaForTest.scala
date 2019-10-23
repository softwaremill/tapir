package tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema.{SArray, SString}
import tapir.{SchemaFor, Validator}

class SchemaForTest extends FlatSpec with Matchers {

  it should "find schema for cats collections" in {
    implicitly[SchemaFor[NonEmptyList[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[NonEmptyList[String]]].isOptional shouldBe false

    implicitly[SchemaFor[NonEmptySet[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[NonEmptySet[String]]].isOptional shouldBe false

    implicitly[SchemaFor[NonEmptyChain[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[NonEmptyChain[String]]].isOptional shouldBe false
  }

  it should "find proper validator for cats collections" in {
    implicitly[Validator[NonEmptyList[String]]].show shouldBe Validator.minSize(1).show

    implicitly[Validator[NonEmptySet[String]]].show shouldBe Validator.minSize(1).show

    implicitly[Validator[NonEmptySet[String]]].show shouldBe Validator.minSize(1).show
  }

}
