package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generic.Derived

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class SchemaApplyValidationTest extends AnyFlatSpec with Matchers {
  import SchemaApplyValidationTestData._

  it should "validate openProduct" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[Map[String, Int]]]

    schema.applyValidation(Map("key" -> 0)).map(noPath(_)) shouldBe List(ValidationError(Validator.min(10), 0))
    schema.applyValidation(Map("key" -> 12)) shouldBe empty
  }

  it should "validate option" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[Option[Int]]]

    schema.applyValidation(None) shouldBe empty
    schema.applyValidation(Some(12)) shouldBe empty
    schema.applyValidation(Some(5)) shouldBe List(ValidationError(Validator.min(10), 5))
  }

  it should "validate iterable" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[List[Int]]]

    schema.applyValidation(List.empty[Int]) shouldBe empty
    schema.applyValidation(List(11)) shouldBe empty
    schema.applyValidation(List(5)) shouldBe List(ValidationError(Validator.min(10), 5))
  }

  it should "validate array" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[Array[Int]]]

    schema.applyValidation(Array.empty[Int]) shouldBe empty
    schema.applyValidation(Array(11)) shouldBe empty
    schema.applyValidation(Array(5)) shouldBe List(ValidationError(Validator.min(10), 5))
  }

  it should "skip collection validation for array if element validator is passing" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.pass)
    val schema = implicitly[Schema[Array[Int]]]

    val bigArray = List.fill(1000000)(1).toArray

    // warm up
    (1 to 10).foreach { _ =>
      schema.applyValidation(bigArray)
    }

    var summaryTime = 0L
    (1 to 100).foreach { _ =>
      val start = System.nanoTime()
      schema.applyValidation(bigArray)
      val end = System.nanoTime()
      summaryTime += (end - start)
    }
    Duration(summaryTime, TimeUnit.NANOSECONDS).toSeconds should be <= 1L
  }

  it should "skip collection validation for iterable if element validator is passing" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.pass)
    val schema = implicitly[Schema[List[Int]]]

    val bigCollection = List.fill(1000000)(1)

    // warm up
    (1 to 10).foreach { _ =>
      schema.applyValidation(bigCollection)
    }

    var summaryTime = 0L
    (1 to 100).foreach { _ =>
      val start = System.nanoTime()
      schema.applyValidation(bigCollection)
      val end = System.nanoTime()
      summaryTime += (end - start)
    }
    Duration(summaryTime, TimeUnit.NANOSECONDS).toSeconds should be <= 1L
  }

  it should "validate product" in {
    case class Person(name: String, age: Int)
    implicit val nameSchema: Schema[String] = Schema.schemaForString.validate(Validator.pattern("^[A-Z].*"))
    implicit val ageSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(18))
    val schema = Schema.derived[Person]
    schema.applyValidation(Person("notImportantButOld", 21)).map(noPath(_)) shouldBe List(
      ValidationError(Validator.pattern("^[A-Z].*"), "notImportantButOld")
    )
    schema.applyValidation(Person("notImportantAndYoung", 15)).map(noPath(_)) shouldBe List(
      ValidationError(Validator.pattern("^[A-Z].*"), "notImportantAndYoung"),
      ValidationError(Validator.min(18), 15)
    )
    schema.applyValidation(Person("ImportantButYoung", 15)).map(noPath(_)) shouldBe List(ValidationError(Validator.min(18), 15))
    schema.applyValidation(Person("ImportantAndOld", 21)) shouldBe empty
  }

  it should "use validators defined when modifying the schema" in {
    import sttp.tapir.generic.auto._
    val s: Schema[SimpleDog] = Schema.derived[SimpleDog].modify(_.name)(_.validate(Validator.minLength(3)))

    s.applyValidation(SimpleDog("a")) should have length 1
  }

  it should "validate recursive values" in {
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    lazy implicit val schema: Schema[RecursiveName] = Schema.derived[RecursiveName]

    schema.applyValidation(RecursiveName("x", None)) shouldBe Nil
    schema.applyValidation(RecursiveName("", None)) shouldBe List(
      ValidationError(Validator.minLength(1), "", List(FieldName("name")))
    )
    schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("x", None))))) shouldBe Nil
    schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("", None))))) shouldBe List(
      ValidationError(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("name")))
    )
    schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("x", Some(Vector(RecursiveName("x", None)))))))) shouldBe Nil
    schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("x", Some(Vector(RecursiveName("", None)))))))) shouldBe List(
      ValidationError(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("subNames"), FieldName("name")))
    )
  }

  it should "show recursive validators" in {
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    lazy implicit val s: Schema[RecursiveName] = Schema.derived[RecursiveName]
    s.showValidators shouldBe Some("name->(length>=1),subNames->(elements(elements(recursive)))")
  }

  it should "validate either" in {
    val schema = Schema.schemaForEither(
      Schema.schemaForInt.validate(Validator.min(1)),
      Schema.schemaForString.validate(Validator.minLength(1))
    )

    schema.applyValidation(Left(10)) shouldBe Nil
    schema.applyValidation(Right("x")) shouldBe Nil

    schema.applyValidation(Left(0)) shouldBe List(ValidationError(Validator.min(1), 0))
    schema.applyValidation(Right("")) shouldBe List(ValidationError(Validator.minLength(1), ""))
  }

  it should "validate mapped either" in {
    case class EitherWrapper[L, R](e: Either[L, R])
    val schema = Schema
      .schemaForEither(
        Schema.schemaForInt.validate(Validator.min(1)),
        Schema.schemaForString.validate(Validator.minLength(1))
      )
      .map(e => Some(EitherWrapper(e)))(_.e)

    schema.applyValidation(EitherWrapper(Left(10))) shouldBe Nil
    schema.applyValidation(EitherWrapper(Right("x"))) shouldBe Nil

    schema.applyValidation(EitherWrapper(Left(0))) shouldBe List(ValidationError(Validator.min(1), 0))
    schema.applyValidation(EitherWrapper(Right(""))) shouldBe List(ValidationError(Validator.minLength(1), ""))
  }

  it should "validate oneOf object" in {
    case class SomeObject(value: String)
    object SomeObject {
      implicit def someObjectSchema: Schema[SomeObject] = Schema.derived[SomeObject].validate(Validator.custom(_ => ValidationResult.Valid))
    }

    sealed trait Entity {
      def kind: String
    }
    object Entity {
      implicit val entitySchema: Schema[Entity] =
        Schema.oneOfUsingField[Entity, String](_.kind, identity)("person" -> Schema.derived[Person])
    }
    case class Person(obj: SomeObject) extends Entity {
      override def kind: String = "person"
    }

    Entity.entitySchema.applyValidation(Person(SomeObject("1234"))) shouldBe Nil
  }

  private def noPath[T](v: ValidationError[T]): ValidationError[T] = v.copy(path = Nil)
}
