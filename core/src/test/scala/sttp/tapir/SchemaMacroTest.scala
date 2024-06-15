package sttp.tapir

import org.scalatest.Assertions
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType._
import sttp.tapir.TestUtil.field
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.D
import sttp.tapir.generic.auto._

class SchemaMacroTest extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks with Inside {
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
        List(
          field(FieldName("name"), Schema(SString())),
          field(FieldName("age"), Schema(SInteger(), format = Some("int32")).description("test").default(10))
        )
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
          List(
            field(FieldName("name"), Schema(SString())),
            field(FieldName("age"), Schema(SInteger(), format = Some("int32")).description("test").default(11))
          )
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
              SOpenProduct[Map[String, Person], Person](Nil, implicitly[Schema[Person]].description("test"))(identity),
              Some(SName("Map", List("sttp.tapir.SchemaMacroTestData.Person")))
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
      SOpenProduct(Nil, implicitly[Schema[V]])((_: Map[String, V]) => Map.empty),
      name = Some(SName("Map", List("sttp.tapir.SchemaMacroTest.V")))
    )

    schema.schemaType.asInstanceOf[SOpenProduct[Map[String, V], V]].mapFieldValues(Map("k" -> V())) shouldBe Map("k" -> V())
  }

  it should "create a map schema with non-string keys" in {
    // given
    case class K()
    case class V()

    // when
    val schema = Schema.schemaForMap[K, V](_ => "k")

    // then
    schema shouldBe Schema(
      SOpenProduct(Nil, implicitly[Schema[V]])((_: Map[K, V]) => Map.empty),
      name = Some(
        SName("Map", List("sttp.tapir.SchemaMacroTest.K", "sttp.tapir.SchemaMacroTest.V"))
      )
    )

    schema.schemaType.asInstanceOf[SOpenProduct[Map[K, V], V]].mapFieldValues(Map(K() -> V())) shouldBe Map("k" -> V())
  }

  it should "work with custom naming configuration" in {
    implicit val customConf: Configuration = Configuration.default.withKebabCaseMemberNames
    val actual = implicitly[Schema[D]].modify(_.someFieldName)(_.description("something"))
    actual.name shouldBe Some(SName("sttp.tapir.generic.D"))
    actual.schemaType shouldBe SProduct[D](
      List(field(FieldName("someFieldName", "some-field-name"), Schema(SString()).description("something")))
    )
  }

  it should "Not propagate type encodedName to subtypes of a sealed trait, but keep inheritance for fields" in {
    val parentSchema = Schema.derived[Hericium]
    val child1Schema = Schema.derived[Hericium.Erinaceus]
    val child2Schema = Schema.derived[Hericium.Botryoides]

    parentSchema.name.map(_.fullName) shouldBe Some("CustomHericium")
    parentSchema.schemaType.asInstanceOf[SCoproduct[Hericium]].subtypes.flatMap(_.name.map(_.fullName)) should contain allOf (
      "sttp.tapir.SchemaMacroTestData.Hericium.Abietis",
      "sttp.tapir.SchemaMacroTestData.Hericium.Botryoides",
      "CustomErinaceus"
    )
    child1Schema.name.map(_.fullName) shouldBe Some("CustomErinaceus")
    child2Schema.name.map(_.fullName) shouldBe Some("sttp.tapir.SchemaMacroTestData.Hericium.Botryoides")
    inside(child2Schema.schemaType.asInstanceOf[SProduct[Hericium.Botryoides]].fields.find(_.name.encodedName == "customCommonField")) {
      case Some(field) =>
        field.schema.name.map(_.fullName) shouldBe None
        field.schema.description shouldBe Some("A common field")
    }
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

    schema.name shouldBe Some(
      SName("sttp.tapir.SchemaMacroTestData.WrapperT", List("java.lang.String", "scala.Int", "java.lang.String"))
    )
  }

  it should "add the discriminator as a field when using oneOfUsingField" in {
    val schema =
      Schema.oneOfUsingField[Entity, String](_.kind, _.toString)("user" -> Schema.derived[User], "org" -> Schema.derived[Organization])
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

    schemaType.subtypes.foreach { childSchema =>
      val childProduct = childSchema.schemaType.asInstanceOf[SProduct[_]]
      childProduct.fields.find(_.name.name == "kind") shouldBe Some(SProductField(FieldName("kind"), Schema.string, (_: Any) => None))
    }
  }

  it should "create a one-of wrapped schema" in {
    val schema = Schema.oneOfWrapped[Entity]
    val schemaType: SCoproduct[Entity] = schema.schemaType.asInstanceOf[SCoproduct[Entity]]

    schemaType.discriminator shouldBe None
    schemaType.subtypes shouldBe List(
      Schema.wrapWithSingleFieldProduct(Schema.derived[Organization]),
      Schema.wrapWithSingleFieldProduct(Schema.derived[User])
    )
    schemaType.subtypeSchema(User("x", "y")) shouldBe Some(
      SchemaWithValue(Schema.wrapWithSingleFieldProduct(Schema.derived[User]), User("x", "y"))
    )
    schemaType.subtypeSchema(Organization("a")) shouldBe Some(
      SchemaWithValue(Schema.wrapWithSingleFieldProduct(Schema.derived[Organization]), Organization("a"))
    )
  }

  it should "create a schema using oneOfField given an enum extractor" in {
    sealed trait YEnum
    case object Y1 extends YEnum

    sealed trait YConf {
      def kind: YEnum
    }
    final case class Y1Conf(size: Int) extends YConf {
      override def kind: YEnum = Y1
    }

    implicit val yEnumSchema: Schema[YEnum] = Schema.derivedEnumeration[YEnum].defaultStringBased

    implicit val yConfSchema: Schema[YConf] = {
      val y1ConfSchema: Schema[Y1Conf] = Schema.derived[Y1Conf]
      Schema.oneOfUsingField[YConf, YEnum](_.kind, _.toString)((Y1: YEnum) -> y1ConfSchema)
    }
  }

  it should "create and enrich a schema for an enum" in {
    val expected = Schema[Letters](SString())
      .validate(
        Validator.enumeration[Letters](
          List(Letters.A, Letters.B, Letters.C),
          (v: Letters) => Option(v),
          Some(SName("sttp.tapir.SchemaMacroTestData.Letters"))
        )
      )
      .description("it's a small alphabet")

    val actual: Schema[Letters] = Schema.derivedEnumeration[Letters].defaultStringBased

    actual.schemaType shouldBe expected.schemaType
    (actual.validator, expected.validator) match {
      case (Validator.Enumeration(va, Some(ea), Some(_)), Validator.Enumeration(ve, Some(ee), Some(ne))) =>
        va shouldBe ve
        ea(Letters.A) shouldBe ee(Letters.A)
        // in Scala2 the name is "sttp.tapir.SchemaMacroTestData.Letters", in Scala3: "sttp.tapir.SchemaMacroTestData$.Letters"
        ne.fullName should endWith("Letters")
      case _ => Assertions.fail()
    }
    actual.description shouldBe expected.description
  }

  it should "derive schema for a scala enumeration and enrich schema" in {
    val expected = Schema[Countries.Country](SString())
      .validate(
        Validator.enumeration[Countries.Country](
          Countries.values.toList,
          (v: Countries.Country) => Option(v),
          Some(SName("sttp.tapir.SchemaMacroTestData.Countries"))
        )
      )
      .description("country")
      .default(Countries.PL)
      .name(SName("country-encoded-name"))

    val actual = implicitly[Schema[Countries.Country]]

    actual.schemaType shouldBe expected.schemaType
    (actual.validator, expected.validator) match {
      case (Validator.Enumeration(va, Some(ea), Some(na)), Validator.Enumeration(ve, Some(ee), Some(ne))) =>
        va shouldBe ve
        ea(Countries.PL) shouldBe ee(Countries.PL)
        na shouldBe ne
      case _ => Assertions.fail()
    }
    actual.description shouldBe expected.description
    actual.default shouldBe expected.default
    actual.name shouldBe expected.name
  }

  it should "derive a customised schema for a scala enumeration and enrich schema" in {
    val expected = Schema[Countries.Country](SInteger())
      .validate(
        Validator.enumeration[Countries.Country](
          Countries.values.toList,
          (v: Countries.Country) => Option(v.id),
          Some(SName("sttp.tapir.SchemaMacroTestData.Countries"))
        )
      )
      .description("country")
      .default(Countries.PL)
      .name(SName("country-encoded-name"))

    val actual = Schema.derivedEnumerationValueCustomise[Countries.Country](encode = Some(_.id), schemaType = SchemaType.SInteger())

    actual.schemaType shouldBe expected.schemaType
    (actual.validator, expected.validator) match {
      case (Validator.Enumeration(va, Some(ea), Some(na)), Validator.Enumeration(ve, Some(ee), Some(ne))) =>
        va shouldBe ve
        ea(Countries.PL) shouldBe ee(Countries.PL)
        na shouldBe ne
      case _ => Assertions.fail()
    }
    actual.description shouldBe expected.description
    actual.default shouldBe expected.default
    actual.name shouldBe expected.name
  }

  behavior of "apply default"

  it should "add default to product" in {
    val expected = Schema(
      SProduct[Person](
        List(field(FieldName("name"), Schema(SString())), field(FieldName("age"), Schema(SInteger(), format = Some("int32")).default(34)))
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Person"))
    )

    implicitly[Schema[Person]].modify(_.age)(_.default(34)) shouldBe expected
  }

  behavior of "apply example"

  it should "add example to product" in {
    val expected = Schema(
      SProduct[Person](
        List(
          field(FieldName("name"), Schema(SString())),
          field(FieldName("age"), Schema(SInteger(), format = Some("int32")).encodedExample(18))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Person"))
    )

    implicitly[Schema[Person]].modify(_.age)(_.encodedExample(18)) shouldBe expected
  }

  behavior of "apply description"

  it should "add description to product" in {
    val expected = Schema(
      SProduct[Person](
        List(
          field(FieldName("name"), Schema(SString())),
          field(FieldName("age"), Schema(SInteger(), format = Some("int32")).description("test"))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Person"))
    )

    implicitly[Schema[Person]].modify(_.age)(_.description("test")) shouldBe expected
  }
}
