package sttp.tapir

import sttp.tapir.SchemaType._
import sttp.tapir.generic.{Configuration, D}
import org.scalatest.flatspec.AnyFlatSpec
import sttp.tapir.generic.auto._
import org.scalatest.matchers.should.Matchers

class SchemaMacroTest extends AnyFlatSpec with Matchers {
  behavior of "apply modification"

  it should "modify basic schema" in {
    implicitly[Schema[String]].modify(x => x)(_.description("test").default("f2")) shouldBe implicitly[Schema[String]]
      .copy(description = Some("test"), default = Some(("f2", None)), isOptional = true)
  }

  it should "modify product schema" in {
    val info1 = SObjectInfo("sttp.tapir.Person")
    implicitly[Schema[Person]]
      .modify(_.age)(_.description("test").default(10)) shouldBe Schema(
      SProduct(info1, List((FieldName("name"), Schema(SString)), (FieldName("age"), Schema(SInteger).description("test").default(10))))
    )
  }

  it should "modify nested product schema" in {
    val info1 = SObjectInfo("sttp.tapir.DevTeam")
    val info2 = SObjectInfo("sttp.tapir.Person")

    val expectedNestedProduct =
      Schema(
        SProduct(info2, List((FieldName("name"), Schema(SString)), (FieldName("age"), Schema(SInteger).description("test").default(11))))
      )

    implicitly[Schema[DevTeam]]
      .modify(_.p1.age)(_.description("test").default(11)) shouldBe
      Schema(SProduct(info1, List((FieldName("p1"), expectedNestedProduct), (FieldName("p2"), implicitly[Schema[Person]]))))
  }

  it should "modify array elements in products" in {
    val info1 = SObjectInfo("sttp.tapir.ArrayWrapper")
    implicitly[Schema[ArrayWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List((FieldName("f1"), Schema(SArray(Schema(SString).format("xyz")), isOptional = true))))
    )
  }

  it should "modify array in products" in {
    val info1 = SObjectInfo("sttp.tapir.ArrayWrapper")
    implicitly[Schema[ArrayWrapper]]
      .modify(_.f1)(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List((FieldName("f1"), Schema(SArray(Schema(SString)), isOptional = true).format("xyz"))))
    )
  }

  it should "support chained modifications" in {
    val info1 = SObjectInfo("sttp.tapir.DevTeam")

    implicitly[Schema[DevTeam]]
      .modify(_.p1)(_.format("xyz"))
      .modify(_.p2)(_.format("qwe")) shouldBe Schema(
      SProduct(
        info1,
        List((FieldName("p1"), implicitly[Schema[Person]].format("xyz")), (FieldName("p2"), implicitly[Schema[Person]].format("qwe")))
      )
    )
  }

  it should "modify optional parameter" in {
    val parentSchema = implicitly[Schema[Parent]]
    parentSchema
      .modify(_.child)(_.format("xyz")) shouldBe Schema(
      SProduct(SObjectInfo("sttp.tapir.Parent"), List(FieldName("child") -> implicitly[Schema[Person]].format("xyz").asOption)),
      validator = parentSchema.validator
    )
  }

  it should "modify property of optional parameter" in {
    val parentSchema = implicitly[Schema[Parent]]
    parentSchema
      .modify(_.child.each.age)(_.format("xyz")) shouldBe Schema(
      SProduct(
        SObjectInfo("sttp.tapir.Parent"),
        List(
          FieldName("child") -> Schema(
            SProduct(
              SObjectInfo("sttp.tapir.Person"),
              List(FieldName("name") -> Schema(SString), FieldName("age") -> Schema(SInteger).format("xyz"))
            ),
            isOptional = true
          )
        )
      ),
      validator = parentSchema.validator
    )
  }

  it should "modify property of open product" in {
    implicitly[Schema[Team]]
      .modify(_.v.each)(_.description("test")) shouldBe Schema(
      SProduct(
        SObjectInfo("sttp.tapir.Team"),
        List(
          FieldName("v") -> Schema(
            SOpenProduct(SObjectInfo("Map", List("Person")), implicitly[Schema[Person]].description("test"))
          )
        )
      )
    )
  }

  it should "modify open product" in {
    val schema = implicitly[Schema[Map[String, String]]]
    schema.modify(x => x)(_.description("test")) shouldBe schema.description("test")
  }

  behavior of "apply default"

  it should "add default to product" in {
    val expected = Schema(
      SProduct(
        SObjectInfo("sttp.tapir.Person"),
        List((FieldName("name"), Schema(SString)), (FieldName("age"), Schema(SInteger).default(34)))
      )
    )

    implicitly[Schema[Person]].modify(_.age)(_.default(34)) shouldBe expected
  }

  behavior of "apply example"

  it should "add example to product" in {
    val expected = Schema(
      SProduct(
        SObjectInfo("sttp.tapir.Person"),
        List((FieldName("name"), Schema(SString)), (FieldName("age"), Schema(SInteger).encodedExample(18)))
      )
    )

    implicitly[Schema[Person]].modify(_.age)(_.encodedExample(18)) shouldBe expected
  }

  behavior of "apply description"

  it should "add description to product" in {
    val expected = Schema(
      SProduct(
        SObjectInfo("sttp.tapir.Person"),
        List((FieldName("name"), Schema(SString)), (FieldName("age"), Schema(SInteger).description("test")))
      )
    )

    implicitly[Schema[Person]].modify(_.age)(_.description("test")) shouldBe expected
  }

  it should "work with custom naming configuration" in {
    implicit val customConf: Configuration = Configuration.default.withKebabCaseMemberNames
    val actual = implicitly[Schema[D]].modify(_.someFieldName)(_.description("something"))
    actual.schemaType shouldBe SProduct(
      SObjectInfo("sttp.tapir.generic.D"),
      List((FieldName("someFieldName", "some-field-name"), Schema(SString).description("something")))
    )
  }
}

case class ArrayWrapper(f1: List[String])
case class Person(name: String, age: Int)
case class DevTeam(p1: Person, p2: Person)
case class Parent(child: Option[Person])
case class Team(v: Map[String, Person])
