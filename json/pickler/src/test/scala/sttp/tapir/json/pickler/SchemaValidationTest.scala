package sttp.tapir.json.pickler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.annotations.{encodedName, validate}
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.{FieldName, Schema, ValidationError, Validator}

/** `Schema.applyValidation` on derived coproducts, which is what `Codec.json` runs on every decoded body.
  *
  * Validation reaches a leaf's fields only through `SCoproduct.subtypeSchema`, the dispatch from a value to its leaf schema. The pickler
  * generates it as a type-test `match`; a runtime class-name comparison would silently return `None` (and hence "valid") for every case
  * below, because `getClass.getName` is `$`-mangled for nested classes and identical for all parameterless enum cases.
  */
class SchemaValidationTest extends AnyFlatSpec with Matchers {
  import ValidationFixtures.*

  private val tooShort = ValidationError(Validator.minLength(3), "ab", List(FieldName("name")))

  behavior of "coproduct validation"

  it should "reach the fields of a leaf nested in an object" in {
    val schema = Pickler.derived[Pet].schema
    schema.applyValidation(Dog("abc")) shouldBe Nil
    schema.applyValidation(Dog("ab")) shouldBe List(tooShort)
    schema.applyValidation(Cat("ab", 1)) shouldBe List(tooShort)
  }

  it should "reach the fields of a leaf below an intermediate sealed trait" in {
    val schema = Pickler.derived[Animal].schema
    schema.applyValidation(Hamster("ab")) shouldBe List(tooShort)
    schema.applyValidation(Hamster("abc")) shouldBe Nil
  }

  it should "tell parameterless Scala 3 enum cases apart from case-class cases" in {
    val schema = Pickler.derived[Shape].schema
    schema.applyValidation(Shape.Circle(0)) shouldBe List(ValidationError(Validator.min(1), 0, List(FieldName("radius"))))
    schema.applyValidation(Shape.Circle(2)) shouldBe Nil
    schema.applyValidation(Shape.Unknown) shouldBe Nil
    // and dispatch each value to its own leaf
    val coproduct = schema.schemaType.asInstanceOf[SCoproduct[Shape]]
    coproduct.subtypeSchema(Shape.Unknown).flatMap(_.schema.name).map(_.fullName.split('.').last) shouldBe Some("Unknown")
    coproduct.subtypeSchema(Shape.Circle(1)).flatMap(_.schema.name).map(_.fullName.split('.').last) shouldBe Some("Circle")
  }

  it should "dispatch to a leaf renamed with a type-level @encodedName" in {
    val schema = Pickler.derived[Pet].schema
    schema.schemaType.asInstanceOf[SCoproduct[Pet]].subtypeSchema(Fish("ab")).flatMap(_.schema.name) shouldBe Some(Schema.SName("Goldfish"))
    schema.applyValidation(Fish("ab")) shouldBe List(tooShort)
  }

  it should "hold for oneOfUsingField as well" in {
    val schema = Pickler
      .oneOfUsingField[Pet, Int](_.legs, legs => s"legs-$legs")(
        4 -> Pickler.derived[Dog],
        3 -> Pickler.derived[Cat],
        0 -> Pickler.derived[Fish]
      )
      .schema
    schema.applyValidation(Dog("ab")) shouldBe List(tooShort)
    schema.applyValidation(Cat("abc", 1)) shouldBe Nil
  }

  it should "validate through a coproduct field of a product" in {
    val schema = Pickler.derived[Owner].schema
    schema.applyValidation(Owner(Dog("ab"))) shouldBe List(tooShort.copy(path = List(FieldName("pet"), FieldName("name"))))
  }
}

object ValidationFixtures {
  sealed trait Pet { def legs: Int }
  case class Dog(@validate(Validator.minLength(3)) name: String) extends Pet { def legs = 4 }
  case class Cat(@validate(Validator.minLength(3)) name: String, lives: Int) extends Pet { def legs = 3 }
  @encodedName("Goldfish")
  case class Fish(@validate(Validator.minLength(3)) name: String) extends Pet { def legs = 0 }
  case class Owner(pet: Pet)

  sealed trait Animal
  sealed trait Rodent extends Animal
  case class Hamster(@validate(Validator.minLength(3)) name: String) extends Rodent
  case class Bird(name: String) extends Animal

  enum Shape:
    case Circle(@validate(Validator.min(1)) radius: Int)
    case Square(side: Int)
    case Unknown
}
