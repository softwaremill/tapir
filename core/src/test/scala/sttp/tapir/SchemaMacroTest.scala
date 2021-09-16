package sttp.tapir

import sttp.tapir.SchemaType._
import sttp.tapir.generic.{Configuration, D}
import org.scalatest.flatspec.AnyFlatSpec
import sttp.tapir.generic.auto._
import org.scalatest.matchers.should.Matchers
import sttp.tapir.TestUtil.field

import org.scalatest.prop.TableDrivenPropertyChecks
import sttp.tapir.Schema.SName

class SchemaMacroTest extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {
  import SchemaMacroTestData._

  behavior of "apply modification"

  it should "modify basic schema" in {
    implicitly[Schema[String]].modify(x => x)(_.description("test").default("f2")) shouldBe implicitly[Schema[String]]
      .copy(description = Some("test"), default = Some(("f2", Some("f2"))), isOptional = true)
  }

  it should "modify product schema" in {
    val name1 = SName("sttp.tapir.SchemaMacroTestData.Person")
    val baseSchema = Schema(
      SProduct[Person](
        List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).description("test").default(10)))
      ),
      Some(name1)
    )

    baseSchema.modify(_.age)(_.description("test").default(10)) shouldBe Schema(
      SProduct[Person](
        List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).description("test").default(10)))
      ),
      Some(name1)
    )
  }

  it should "modify product schema with derivation" in {
    val name1 = SName("sttp.tapir.SchemaMacroTestData.Person")
    implicitly[Schema[Person]]
      .modify(_.age)(_.description("test").default(10)) shouldBe Schema(
      SProduct[Person](
        List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).description("test").default(10)))
      ),
      Some(name1)
    )
  }

  it should "modify nested product schema" in {
    val name1 = SName("sttp.tapir.SchemaMacroTestData.DevTeam")
    val name2 = SName("sttp.tapir.SchemaMacroTestData.Person")

    val expectedNestedProduct =
      Schema(
        SProduct[Person](
          List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).description("test").default(11)))
        ),
        Some(name2)
      )

    implicitly[Schema[DevTeam]]
      .modify(_.p1.age)(_.description("test").default(11)) shouldBe
      Schema(
        SProduct[DevTeam](List(field(FieldName("p1"), expectedNestedProduct), field(FieldName("p2"), implicitly[Schema[Person]]))),
        Some(name1)
      )
  }

  it should "modify array elements in products" in {
    val name1 = SName("sttp.tapir.SchemaMacroTestData.ArrayWrapper")
    implicitly[Schema[ArrayWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldBe Schema(
      SProduct[ArrayWrapper](
        List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()).format("xyz"))(identity), isOptional = true)))
      ),
      Some(name1)
    )
  }

  it should "modify array in products" in {
    val name1 = SName("sttp.tapir.SchemaMacroTestData.ArrayWrapper")
    implicitly[Schema[ArrayWrapper]]
      .modify(_.f1)(_.format("xyz")) shouldBe Schema(
      SProduct[ArrayWrapper](
        List(
          field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(identity), isOptional = true).format("xyz"))
        )
      ),
      Some(name1)
    )
  }

  it should "support chained modifications" in {
    val name1 = SName("sttp.tapir.SchemaMacroTestData.DevTeam")

    implicitly[Schema[DevTeam]]
      .modify(_.p1)(_.format("xyz"))
      .modify(_.p2)(_.format("qwe")) shouldBe Schema(
      SProduct[DevTeam](
        List(
          field(FieldName("p1"), implicitly[Schema[Person]].format("xyz")),
          field(FieldName("p2"), implicitly[Schema[Person]].format("qwe"))
        )
      ),
      Some(name1)
    )
  }

  it should "modify optional parameter" in {
    val parentSchema = implicitly[Schema[Parent]]
    parentSchema
      .modify(_.child)(_.format("xyz")) shouldBe Schema(
      SProduct[Parent](
        List(field(FieldName("child"), implicitly[Schema[Person]].asOption.format("xyz")))
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Parent")),
      validator = parentSchema.validator
    )
  }

  it should "modify property of optional parameter" in {
    val parentSchema = implicitly[Schema[Parent]]
    parentSchema
      .modify(_.child.each.age)(_.format("xyz")) shouldBe Schema(
      SProduct[Parent](
        List(
          field(
            FieldName("child"),
            Schema(
              SProduct[Person](
                List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).format("xyz")))
              ),
              Some(SName("sttp.tapir.SchemaMacroTestData.Person"))
            ).asOption
          )
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Parent")),
      validator = parentSchema.validator
    )
  }

  it should "modify property of open product" in {
    implicitly[Schema[Team]]
      .modify(_.v.each)(_.description("test")) shouldBe Schema(
      SProduct[Team](
        List(
          field(
            FieldName("v"),
            Schema(
              SOpenProduct[Map[String, Person], Person](implicitly[Schema[Person]].description("test"))(identity),
              Some(SName("Map", List("Person")))
            )
          )
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Team"))
    )
  }

  it should "modify open product" in {
    val schema = implicitly[Schema[Map[String, String]]]
    schema.modify(x => x)(_.description("test")) shouldBe schema.description("test")
  }

  it should "create a map schema with string keys" in {
    // given
    case class V()

    // when
    val schema = Schema.schemaForMap[V]

    // then
    schema shouldBe Schema(
      SOpenProduct(implicitly[Schema[V]])((_: Map[String, V]) => Map.empty),
      name = Some(SName("Map", List("V")))
    )

    schema.schemaType.asInstanceOf[SOpenProduct[Map[String, V], V]].fieldValues(Map("k" -> V())) shouldBe Map("k" -> V())
  }

  it should "create a map schema with non-string keys" in {
    // given
    case class K()
    case class V()

    // when
    val schema = Schema.schemaForMap[K, V](_ => "k")

    // then
    schema shouldBe Schema(
      SOpenProduct(implicitly[Schema[V]])((_: Map[K, V]) => Map.empty),
      name = Some(SName("Map", List("K", "V")))
    )

    schema.schemaType.asInstanceOf[SOpenProduct[Map[K, V], V]].fieldValues(Map(K() -> V())) shouldBe Map("k" -> V())
  }

  it should "work with custom naming configuration" in {
    implicit val customConf: Configuration = Configuration.default.withKebabCaseMemberNames
    val actual = implicitly[Schema[D]].modify(_.someFieldName)(_.description("something"))
    actual.name shouldBe Some(SName("sttp.tapir.generic.D"))
    actual.schemaType shouldBe SProduct[D](
      List(field(FieldName("someFieldName", "some-field-name"), Schema(SString()).description("something")))
    )
  }

  it should "add discriminator based on a trait method" in {
    val sUser = Schema.derived[User]
    val sOrganization = Schema.derived[Organization]

    forAll(
      Table(
        "schema",
        Schema.oneOfUsingField[Wrapper, String]({ x => x.e.kind(x.s) }, _.toString)("user" -> sUser, "org" -> sOrganization).schemaType,
        Schema.oneOfUsingField[Wrapper, String](x => x.e.kind(x.s), _.toString)("user" -> sUser, "org" -> sOrganization).schemaType,
        Schema.oneOfUsingField[Wrapper, String](_.e.kind, _.toString)("user" -> sUser, "org" -> sOrganization).schemaType,
        Schema.oneOfUsingField[Entity, String](_.kind, _.toString)("user" -> sUser, "org" -> sOrganization).schemaType,
        Schema.oneOfUsingField[Entity, String]({ x => x.kind }, _.toString)("user" -> sUser, "org" -> sOrganization).schemaType,
        Schema.oneOfUsingField[Entity, String]({ _.kind }, _.toString)("user" -> sUser, "org" -> sOrganization).schemaType
      )
    ) {
      _.asInstanceOf[SCoproduct[_]].discriminator shouldBe Some(
        SDiscriminator(
          FieldName("kind"),
          Map(
            "user" -> SRef(SName("sttp.tapir.SchemaMacroTestData.User")),
            "org" -> SRef(SName("sttp.tapir.SchemaMacroTestData.Organization"))
          )
        )
      )
    }
  }

  it should "extract type params for discriminator based on a trait method" in {
    val sUser = Schema.derived[User]
    val sOrganization = Schema.derived[Organization]

    val schema = Schema
      .oneOfUsingField[WrapperT[String, Int, String], String]({ x => x.e.kind(x.a) }, _.toString)("user" -> sUser, "org" -> sOrganization)
    val schemaType = schema.schemaType.asInstanceOf[SCoproduct[_]]

    schemaType.discriminator shouldBe Some(
      SDiscriminator(
        FieldName("kind"),
        Map(
          "user" -> SRef(SName("sttp.tapir.SchemaMacroTestData.User")),
          "org" -> SRef(SName("sttp.tapir.SchemaMacroTestData.Organization"))
        )
      )
    )

    schema.name shouldBe Some(SName("sttp.tapir.SchemaMacroTestData.WrapperT", List("String", "Int", "String")))
  }

  behavior of "apply default"

  it should "add default to product" in {
    val expected = Schema(
      SProduct[Person](
        List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).default(34)))
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Person"))
    )

    implicitly[Schema[Person]].modify(_.age)(_.default(34)) shouldBe expected
  }

  behavior of "apply example"

  it should "add example to product" in {
    val expected = Schema(
      SProduct[Person](
        List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).encodedExample(18)))
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Person"))
    )

    implicitly[Schema[Person]].modify(_.age)(_.encodedExample(18)) shouldBe expected
  }

  behavior of "apply description"

  it should "add description to product" in {
    val expected = Schema(
      SProduct[Person](
        List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger()).description("test")))
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Person"))
    )

    implicitly[Schema[Person]].modify(_.age)(_.description("test")) shouldBe expected
  }

}
