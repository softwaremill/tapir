package tapir.generic

import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema._
import tapir.{Schema, SchemaFor}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SchemaForTest extends FlatSpec with Matchers {

  "SchemaFor" should "find schema for simple types" in {
    implicitly[SchemaFor[String]].schema shouldBe SString
    implicitly[SchemaFor[String]].isOptional shouldBe false

    implicitly[SchemaFor[Short]].schema shouldBe SInteger
    implicitly[SchemaFor[Int]].schema shouldBe SInteger
    implicitly[SchemaFor[Long]].schema shouldBe SInteger
    implicitly[SchemaFor[Float]].schema shouldBe SNumber
    implicitly[SchemaFor[Double]].schema shouldBe SNumber
    implicitly[SchemaFor[Boolean]].schema shouldBe SBoolean
  }

  it should "find schema for optional types" in {
    implicitly[SchemaFor[Option[String]]].schema shouldBe SString
    implicitly[SchemaFor[Option[String]]].isOptional shouldBe true
  }

  it should "find schema for collections" in {
    implicitly[SchemaFor[Array[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[Array[String]]].isOptional shouldBe false

    implicitly[SchemaFor[List[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[List[String]]].isOptional shouldBe false

    implicitly[SchemaFor[Set[String]]].schema shouldBe SArray(SString)
  }

  val expectedASchema =
    SObject(SObjectInfo("A", "tapir.generic.A"), List(("f1", SString), ("f2", SInteger), ("f3", SString)), List("f1", "f2"))

  it should "find schema for collections of case classes" in {
    implicitly[SchemaFor[List[A]]].schema shouldBe SArray(expectedASchema)
  }

  it should "find schema for a simple case class" in {
    implicitly[SchemaFor[A]].schema shouldBe expectedASchema
  }

  val expectedDSchema: SObject = SObject(SObjectInfo("D", "tapir.generic.D"), List(("someFieldName", SString)), List("someFieldName"))

  it should "find schema for a simple case class and use identity naming transformation" in {
    implicitly[SchemaFor[D]].schema shouldBe expectedDSchema
  }

  it should "find schema for a simple case class and use snake case naming transformation" in {
    val expectedSnakeCaseNaming = expectedDSchema.copy(fields = List(("some_field_name", SString)), required = List("some_field_name"))
    implicit val customConf = Configuration.default.withSnakeCaseMemberNames
    implicitly[SchemaFor[D]].schema shouldBe expectedSnakeCaseNaming
  }

  it should "find schema for a simple case class and use kebab case naming transformation" in {
    val expectedKebabCaseNaming = expectedDSchema.copy(fields = List(("some-field-name", SString)), required = List("some-field-name"))
    implicit val customConf = Configuration.default.withKebabCaseMemberNames
    implicitly[SchemaFor[D]].schema shouldBe expectedKebabCaseNaming
  }

  it should "find schema for a nested case class" in {
    implicitly[SchemaFor[B]].schema shouldBe SObject(
      SObjectInfo("B", "tapir.generic.B"),
      List(("g1", SString), ("g2", expectedASchema)),
      List("g1", "g2")
    )
  }

  it should "find schema for case classes with collections" in {
    implicitly[SchemaFor[C]].schema shouldBe SObject(
      SObjectInfo("C", "tapir.generic.C"),
      List(("h1", SArray(SString)), ("h2", SInteger)),
      List("h1")
    )
  }

  it should "find schema for recursive data structure" in {
    val schema = implicitly[SchemaFor[F]].schema
    schema shouldBe SObject(
      SObjectInfo("F", "tapir.generic.F"),
      List(("f1", SArray(SRef("tapir.generic.F"))), ("f2", SInteger)),
      List("f1", "f2")
    )
  }

  it should "find schema for recursive data structure when invoked from many threads" in {
    val expected =
      SObject(SObjectInfo("F", "tapir.generic.F"), List(("f1", SArray(SRef("tapir.generic.F"))), ("f2", SInteger)), List("f1", "f2"))

    val count = 100
    val futures = (1 until count).map { _ =>
      Future[Schema] {
        implicitly[SchemaFor[F]].schema
      }
    }

    val eventualSchemas = Future.sequence(futures)

    val schemas = Await.result(eventualSchemas, 5 seconds)
    schemas should contain only expected
  }

  it should "use custom schema for custom types" in {
    implicit val scustom: SchemaFor[Custom] = SchemaFor[Custom](Schema.SString)
    val schema = implicitly[SchemaFor[G]].schema
    schema shouldBe SObject(SObjectInfo("G", "tapir.generic.G"), List(("f1", SInteger), ("f2", SString)), List("f1", "f2"))
  }

  it should "derive schema for parametrised type classes" in {
    val schema = implicitly[SchemaFor[H[A]]].schema
    schema shouldBe SObject(SObjectInfo("H", "tapir.generic.H"), List(("data", expectedASchema)), List("data"))
  }
}

case class A(f1: String, f2: Int, f3: Option[String])
case class B(g1: String, g2: A)
case class C(h1: List[String], h2: Option[Int])
case class D(someFieldName: String)
case class F(f1: List[F], f2: Int)

class Custom(c: String)
case class G(f1: Int, f2: Custom)

case class H[T](data: T)
