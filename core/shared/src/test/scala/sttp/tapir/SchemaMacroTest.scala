package sttp.tapir

import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.SchemaType._

class SchemaMacroTest extends FlatSpec with Matchers {
  it should "modify basic schema" in {
    implicitly[Schema[String]].modify(x => x)(_.description("test")) shouldBe implicitly[Schema[String]]
      .copy(description = Some("test"))
  }

  it should "modify product schema" in {
    val info1 = SObjectInfo("sttp.tapir.Person")
    implicitly[Schema[Person]]
      .modify(_.age)(_.description("test")) shouldBe Schema(
      SProduct(info1, List(("name", Schema(SString)), ("age", Schema(SInteger).description("test"))))
    )
  }

  it should "modify nested product schema" in {
    val info1 = SObjectInfo("sttp.tapir.DevTeam")
    val info2 = SObjectInfo("sttp.tapir.Person")

    val expectedNestedProduct = Schema(SProduct(info2, List(("name", Schema(SString)), ("age", Schema(SInteger).description("test")))))

    implicitly[Schema[DevTeam]]
      .modify(_.p1.age)(_.description("test")) shouldBe
      Schema(SProduct(info1, List(("p1", expectedNestedProduct), ("p2", implicitly[Schema[Person]]))))
  }

  it should "modify array elements in products" in {
    val info1 = SObjectInfo("sttp.tapir.ArrayWrapper")
    implicitly[Schema[ArrayWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SArray(Schema(SString).format("xyz")), isOptional = true))))
    )
  }

  it should "modify array in products" in {
    val info1 = SObjectInfo("sttp.tapir.ArrayWrapper")
    implicitly[Schema[ArrayWrapper]]
      .modify(_.f1)(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SArray(Schema(SString)), isOptional = true).format("xyz"))))
    )
  }

  it should "support chained modifications" in {
    val info1 = SObjectInfo("sttp.tapir.DevTeam")

    implicitly[Schema[DevTeam]]
      .modify(_.p1)(_.format("xyz"))
      .modify(_.p2)(_.format("qwe")) shouldBe Schema(
      SProduct(info1, List(("p1", implicitly[Schema[Person]].format("xyz")), ("p2", implicitly[Schema[Person]].format("qwe"))))
    )
  }

  it should "modify optional parameter" in {
    implicitly[Schema[Parent]]
      .modify(_.child)(_.format("xyz")) shouldBe Schema(
      SProduct(SObjectInfo("sttp.tapir.Parent"), List("child" -> implicitly[Schema[Person]].format("xyz").asOptional))
    )
  }

  it should "modify property of optional parameter" in {
    implicitly[Schema[Parent]]
      .modify(_.child.each.age)(_.format("xyz")) shouldBe Schema(
      SProduct(
        SObjectInfo("sttp.tapir.Parent"),
        List(
          "child" -> Schema(
            SProduct(SObjectInfo("sttp.tapir.Person"), List("name" -> Schema(SString), "age" -> Schema(SInteger).format("xyz"))),
            isOptional = true
          )
        )
      )
    )
  }

  it should "modify property of open product" in {
    implicitly[Schema[Team]]
      .modify(_.v.each)(_.description("test")) shouldBe Schema(
      SProduct(
        SObjectInfo("sttp.tapir.Team"),
        List("v" -> Schema(SOpenProduct(SObjectInfo("Map", List("Person")), implicitly[Schema[Person]].description("test"))))
      )
    )
  }

  it should "modify open product" in {
    val schema = implicitly[Schema[Map[String, String]]]
    schema.modify(x => x)(_.description("test")) shouldBe schema.description("test")
  }
}

case class ArrayWrapper(f1: List[String])
case class Person(name: String, age: Int)
case class DevTeam(p1: Person, p2: Person)
case class Parent(child: Option[Person])
case class Team(v: Map[String, Person])
