package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generic.Derived
import sttp.tapir.testing.ValidationResultMatchers

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class SchemaApplyValidationTest extends AnyFlatSpec with Matchers with ValidationResultMatchers {
  import SchemaApplyValidationTestData._

  it should "validate openProduct" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[Map[String, Int]]]

    schema.applyValidation(Map("key" -> 12)) shouldBe valid(Map("key" -> 12))
    schema.applyValidation(Map("key" -> 0)) shouldBe invalid(Map("key" -> 0))
  }

  it should "validate option" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[Option[Int]]]

    schema.applyValidation(None) shouldBe valid[Option[Int]](None)
    schema.applyValidation(Some(12)) shouldBe valid[Option[Int]](Some(12))
    schema.applyValidation(Some(5)) shouldBe invalid[Option[Int]](Some(5))
  }

  it should "validate iterable" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[List[Int]]]

    schema.applyValidation(List.empty[Int]) shouldBe valid(List.empty[Int])
    schema.applyValidation(List(11)) shouldBe valid(List(11))
    schema.applyValidation(List(5)) shouldBe invalid(List(5))
  }

  it should "validate array" in {
    implicit val schemaForInt: Schema[Int] = Schema.schemaForInt.validate(Validator.min(10))
    val schema = implicitly[Schema[Array[Int]]]

    schema.applyValidation(Array.empty[Int]) shouldBe valid
    schema.applyValidation(Array(11)) shouldBe valid
    schema.applyValidation(Array(5)) shouldBe invalid
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

    schema.applyValidation(Person("notImportantButOld", 21)) shouldBe invalid(Person("notImportantButOld", 21))
    schema.applyValidation(Person("notImportantAndYoung", 15)) shouldBe invalidWithMultipleErrors(Person("notImportantAndYoung", 15), 2)
    schema.applyValidation(Person("ImportantButYoung", 15)) shouldBe invalid(Person("ImportantButYoung", 15))
    schema.applyValidation(Person("ImportantAndOld", 21)) shouldBe valid(Person("ImportantAndOld", 21))
  }

  it should "use validators defined when modifying the schema" in {
    import sttp.tapir.generic.auto._
    val s: Schema[SimpleDog] = implicitly[Derived[Schema[SimpleDog]]].value.modify(_.name)(_.validate(Validator.minLength(3)))

    s.applyValidation(SimpleDog("a")) shouldBe invalid(SimpleDog("a"))
  }

  it should "validate recursive values" in {
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    lazy implicit val schema: Schema[RecursiveName] = Schema.derived[RecursiveName]

    val valid1 = RecursiveName("x", None)
    val invalid1 = RecursiveName("", None)
    schema.applyValidation(valid1) shouldBe valid(valid1)
    schema.applyValidation(invalid1) shouldBe invalid(invalid1)

    val valid2 = RecursiveName("x", Some(Vector(valid1)))
    val invalid2 = RecursiveName("x", Some(Vector(invalid1)))
    schema.applyValidation(valid2) shouldBe valid(valid2)
    schema.applyValidation(invalid2) shouldBe invalid(invalid2)

    val valid3 = RecursiveName("x", Some(Vector(valid2)))
    val invalid3 = RecursiveName("x", Some(Vector(invalid2)))
    schema.applyValidation(valid3) shouldBe valid(valid3)
    schema.applyValidation(invalid3) shouldBe invalid(invalid3)
  }

  it should "show recursive validators" in {
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    lazy implicit val s: Schema[RecursiveName] = Schema.derived[RecursiveName]
    s.showValidators shouldBe Some("name->(length>=1),subNames->(elements(elements(recursive)))")
  }
}
