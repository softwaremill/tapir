package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LegacySchemaApplyValidationTest extends AnyFlatSpec with Matchers {
  import SchemaApplyValidationTestData._

  // currently in Scala 3, we use mirror based derivation, therefore we are not able to derive an instance for a
  // two-level hierarchy - https://dotty.epfl.ch/docs/reference/contextual/derivation.html#types-supporting-derives-clauses
  // #946: derivation of validator twice in the same derivation
  it should "validate recursive values with two-level hierarchy" in {
    import sttp.tapir.generic.auto._
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    val schema: Schema[Animal] = implicitly[Schema[Animal]]

    schema.applyValidation(Dog("reksio1", Nil)) shouldBe Nil
    schema.applyValidation(Dog("", Nil)) should have length 1
    schema.applyValidation(Dog("reksio1", List(Dog("reksio2", Nil)))) shouldBe Nil
    schema.applyValidation(Dog("reksio1", List(Dog("", Nil)))) should have length 1
    schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Dog("reksio3", Nil)))))) shouldBe Nil
    schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Dog("", Nil)))))) should have length 1

    schema.applyValidation(Cat("tom1", Nil)) shouldBe Nil
    schema.applyValidation(Cat("", Nil)) should have length 1
    schema.applyValidation(Cat("tom1", List(Cat("tom2", Nil)))) shouldBe Nil
    schema.applyValidation(Cat("tom1", List(Cat("", Nil)))) should have length 1
    schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Cat("tom3", Nil)))))) shouldBe Nil
    schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Cat("", Nil)))))) should have length 1

    schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Cat("tom3", Nil)))))) shouldBe Nil
    schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Cat("", Nil)))))) should have length 1

    schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Dog("reksio3", Nil)))))) shouldBe Nil
    schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Dog("", Nil)))))) should have length 1
  }
}
