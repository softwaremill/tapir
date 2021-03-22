package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidateTest extends AnyFlatSpec with Matchers {
  it should "validate product" in {
    case class Person(name: String, age: Int)
    implicit val nameSchema: Schema[String] = Schema.schemaForString.validate(Validator.pattern("^[A-Z].*"))
    implicit val ageSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(18))
    val validator = Schema.derived[Person].validator
    validator.validate(Person("notImportantButOld", 21)).map(noPath(_)) shouldBe List(
      ValidationError.Primitive(Validator.pattern("^[A-Z].*"), "notImportantButOld")
    )
    validator.validate(Person("notImportantAndYoung", 15)).map(noPath(_)) shouldBe List(
      ValidationError.Primitive(Validator.pattern("^[A-Z].*"), "notImportantAndYoung"),
      ValidationError.Primitive(Validator.min(18), 15)
    )
    validator.validate(Person("ImportantButYoung", 15)).map(noPath(_)) shouldBe List(ValidationError.Primitive(Validator.min(18), 15))
    validator.validate(Person("ImportantAndOld", 21)) shouldBe empty
  }

  it should "validate a custom case class" in {
    case class InnerCaseClass(innerValue: Long)
    case class MyClass(name: String, age: Int, field: InnerCaseClass)
    val validator = Validator.custom[MyClass](doValidate = { v =>
      val nameErrors =
        if (v.name.length < 3) List(ValidationError.Custom(v.name, "Name length should be >= 3", List(FieldName("name", "name"))))
        else List.empty
      val ageErrors =
        if (v.age <= 0) List(ValidationError.Custom(v.age, "Age should be > 0", List(FieldName("age", "age")))) else List.empty
      val innerErrors =
        if (v.field.innerValue <= 0)
          List(
            ValidationError.Custom(
              v.field.innerValue,
              "Inner value should be > 0",
              List(FieldName("field", "field"), FieldName("innerValue", "innerValue"))
            )
          )
        else List.empty
      nameErrors ++ ageErrors ++ innerErrors
    })

    validator.validate(MyClass("ab", -1, InnerCaseClass(-3))) shouldBe List(
      ValidationError.Custom("ab", "Name length should be >= 3", List(FieldName("name", "name"))),
      ValidationError.Custom(-1, "Age should be > 0", List(FieldName("age", "age"))),
      ValidationError.Custom(-3, "Inner value should be > 0", List(FieldName("field", "field"), FieldName("innerValue", "innerValue")))
    )
  }

  it should "validate recursive values" in {
    import sttp.tapir.generic.auto._
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    val v: Validator[RecursiveName] = implicitly[Schema[RecursiveName]].validator

    v.validate(RecursiveName("x", None)) shouldBe Nil
    v.validate(RecursiveName("", None)) shouldBe List(ValidationError.Primitive(Validator.minLength(1), "", List(FieldName("name"))))
    v.validate(RecursiveName("x", Some(Vector(RecursiveName("x", None))))) shouldBe Nil
    v.validate(RecursiveName("x", Some(Vector(RecursiveName("", None))))) shouldBe List(
      ValidationError.Primitive(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("name")))
    )
    v.validate(RecursiveName("x", Some(Vector(RecursiveName("x", Some(Vector(RecursiveName("x", None)))))))) shouldBe Nil
    v.validate(RecursiveName("x", Some(Vector(RecursiveName("x", Some(Vector(RecursiveName("", None)))))))) shouldBe List(
      ValidationError.Primitive(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("subNames"), FieldName("name")))
    )
  }

  it should "show recursive validators" in {
    import sttp.tapir.generic.auto._
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    val v: Validator[RecursiveName] = implicitly[Schema[RecursiveName]].validator
    v.show shouldBe Some("name->(length>=1),subNames->(elements(elements(recursive)))")
  }

  // #946: derivation of validator twice in the same derivation
  it should "validate recursive values with two-level hierarchy" in {
    import sttp.tapir.generic.auto._
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    val v: Validator[Animal] = implicitly[Schema[Animal]].validator

    v.validate(Dog("reksio1", Nil)) shouldBe Nil
    v.validate(Dog("", Nil)) should have length 1
    v.validate(Dog("reksio1", List(Dog("reksio2", Nil)))) shouldBe Nil
    v.validate(Dog("reksio1", List(Dog("", Nil)))) should have length 1
    v.validate(Dog("reksio1", List(Dog("reksio2", List(Dog("reksio3", Nil)))))) shouldBe Nil
    v.validate(Dog("reksio1", List(Dog("reksio2", List(Dog("", Nil)))))) should have length 1

    v.validate(Cat("tom1", Nil)) shouldBe Nil
    v.validate(Cat("", Nil)) should have length 1
    v.validate(Cat("tom1", List(Cat("tom2", Nil)))) shouldBe Nil
    v.validate(Cat("tom1", List(Cat("", Nil)))) should have length 1
    v.validate(Cat("tom1", List(Cat("tom2", List(Cat("tom3", Nil)))))) shouldBe Nil
    v.validate(Cat("tom1", List(Cat("tom2", List(Cat("", Nil)))))) should have length 1

    v.validate(Dog("reksio1", List(Dog("reksio2", List(Cat("tom3", Nil)))))) shouldBe Nil
    v.validate(Dog("reksio1", List(Dog("reksio2", List(Cat("", Nil)))))) should have length 1

    v.validate(Cat("tom1", List(Cat("tom2", List(Dog("reksio3", Nil)))))) shouldBe Nil
    v.validate(Cat("tom1", List(Cat("tom2", List(Dog("", Nil)))))) should have length 1
  }

  private def noPath[T](v: ValidationError[T]): ValidationError[T] =
    v match {
      case p: ValidationError.Primitive[T] => p.copy(path = Nil)
      case c: ValidationError.Custom[T]    => c.copy(path = Nil)
    }
}

final case class RecursiveName(name: String, subNames: Option[Vector[RecursiveName]])

sealed trait Animal
sealed trait Pet extends Animal {}
case class Dog(name: String, friends: List[Pet]) extends Pet
case class Cat(name: String, friends: List[Pet]) extends Pet
