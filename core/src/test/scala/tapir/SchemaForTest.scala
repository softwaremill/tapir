package tapir

import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema._

class SchemaForTest extends FlatSpec with Matchers {
  it should "find schema for simple types" in {
    implicitly[SchemaFor[String]].schema shouldBe SString
    implicitly[SchemaFor[String]].isOptional shouldBe false

    implicitly[SchemaFor[Int]].schema shouldBe SInt
  }

  it should "find schema for optional types" in {
    implicitly[SchemaFor[Option[String]]].schema shouldBe SString
    implicitly[SchemaFor[Option[String]]].isOptional shouldBe true
  }

  val expectedASchema = SObject(SObjectInfo("A", "tapir.A"), List(("f1", SString), ("f2", SInt), ("f3", SString)), List("f1", "f2"))

  it should "find schema for a simple case class" in {
    implicitly[SchemaFor[A]].schema shouldBe expectedASchema
  }

  it should "find schema for a nested case class" in {
    implicitly[SchemaFor[B]].schema shouldBe SObject(SObjectInfo("B", "tapir.B"),
                                                     List(("g1", SString), ("g2", expectedASchema)),
                                                     List("g1", "g2"))
  }
}

case class A(f1: String, f2: Int, f3: Option[String])
case class B(g1: String, g2: A)
