package sttp.tapir.json.pickler

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Inside
import sttp.tapir.Schema.annotations.*
import sttp.tapir.Schema.{SName, schemaForBoolean}
import sttp.tapir.SchemaMacroTestData.*
import sttp.tapir.SchemaType._
import sttp.tapir.TestUtil.field
import sttp.tapir.{AttributeKey, FieldName, Schema, SchemaType, Validator}

import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}

class SchemaGenericAutoTest extends AsyncFlatSpec with Matchers with Inside {
  import SchemaGenericAutoTest._

  import generic.auto._
  def implicitlySchema[T: Pickler]: Schema[T] = summon[Pickler[T]].schema

  "Schema auto derivation" should "find schema for simple types" in {
    stringSchema.schemaType shouldBe SString()
    stringSchema.isOptional shouldBe false

    implicitlySchema[Short].schemaType shouldBe SInteger()
    intSchema.schemaType shouldBe SInteger()
    longSchema.schemaType shouldBe SInteger()
    implicitlySchema[Float].schemaType shouldBe SNumber()
    implicitlySchema[Double].schemaType shouldBe SNumber()
    implicitlySchema[Boolean].schemaType shouldBe SBoolean()
    implicitlySchema[BigDecimal].schemaType shouldBe SNumber()
    implicitlySchema[JBigDecimal].schemaType shouldBe SNumber()
    implicitlySchema[JBigInteger].schemaType shouldBe SInteger()
  }

  it should "find schema for optional types" in {
    implicitlySchema[Option[String]].schemaType shouldBe SOption[Option[String], String](Schema(SString()))(identity)
    implicitlySchema[Option[String]].isOptional shouldBe true
  }

  it should "find schema for collections" in {
    implicitlySchema[Array[String]].schemaType shouldBe SArray[Array[String], String](stringSchema)(_.toIterable)
    implicitlySchema[Array[String]].isOptional shouldBe true

    implicitlySchema[List[String]].schemaType shouldBe SArray[List[String], String](stringSchema)(_.toIterable)
    implicitlySchema[List[String]].isOptional shouldBe true

    implicitlySchema[Set[String]].schemaType shouldBe SArray[Set[String], String](stringSchema)(_.toIterable)
    implicitlySchema[Set[String]].isOptional shouldBe true
  }

  val expectedASchema: Schema[A] =
    Schema[A](
      SProduct(
        List(field(FieldName("f1"), stringSchema), field(FieldName("f2"), intSchema), field(FieldName("f3"), stringSchema.asOption))
      ),
      Some(SName("sttp.tapir.json.pickler.A"))
    )

  case class ListA(fl: List[A])

  it should "find schema for collections of case classes" in {
    implicitlySchema[ListA].schemaType shouldBe SProduct(
      List(SProductField(FieldName("fl"), Schema(SArray[List[A], A](expectedASchema)(_.toIterable), isOptional = true), _ => None))
    )
  }

  it should "find schema for a simple case class" in {
    implicitlySchema[A] shouldBe expectedASchema
    implicitlySchema[A].schemaType.asInstanceOf[SProduct[A]].required shouldBe List(FieldName("f1"), FieldName("f2"))
  }

  it should "find schema for a simple case class and use identity naming transformation" in {
    implicitlySchema[D].schemaType shouldBe expectedDSchema
  }

  it should "find schema for a nested case class" in {
    implicitlySchema[B].name shouldBe Some(SName("sttp.tapir.json.pickler.B"))
    implicitlySchema[B].schemaType shouldBe SProduct[B](
      List(field(FieldName("g1"), stringSchema), field(FieldName("g2"), expectedASchema))
    )
  }

  it should "find schema for case classes with collections" in {
    implicitlySchema[C].name shouldBe Some(SName("sttp.tapir.json.pickler.C"))
    implicitlySchema[C].schemaType shouldBe SProduct[C](
      List(field(FieldName("h1"), stringSchema.asArray), field(FieldName("h2"), intSchema.asOption))
    )
    implicitlySchema[C].schemaType.asInstanceOf[SProduct[C]].required shouldBe Nil
  }

  // it should "use custom schema for custom types" in { // TODO
  //   implicit val scustom: Schema[Custom] = Schema[Custom](SchemaType.SString())
  //   val schema = Pickler.derived[G].schema
  //   schema.name shouldBe Some(SName("sttp.tapir.json.pickler.G"))
  //   schema.schemaType shouldBe SProduct[G](
  //     List(field(FieldName("f1"), intSchema), field(FieldName("f2"), stringSchema))
  //   )
  // }

  it should "derive schema for parametrised type classes" in {
    val schema = implicitlySchema[H[A]]
    schema.name shouldBe Some(SName("sttp.tapir.json.pickler.H", List("sttp.tapir.json.pickler.A")))
    schema.schemaType shouldBe SProduct[H[A]](List(field(FieldName("data"), expectedASchema)))
  }

  it should "find schema for map" in {
    val schema = implicitlySchema[Map[String, Int]]
    schema.name shouldBe Some(SName("Map", List("scala.Int")))
    schema.schemaType shouldBe SOpenProduct[Map[String, Int], Int](Nil, intSchema)(identity)
  }

  it should "find schema for map of products" in {
    val schema = implicitlySchema[Map[String, D]]
    schema.name shouldBe Some(SName("Map", List("sttp.tapir.json.pickler.D")))
    schema.schemaType shouldBe SOpenProduct[Map[String, D], D](
      Nil,
      Schema(SProduct(List(field(FieldName("someFieldName"), stringSchema))), Some(SName("sttp.tapir.json.pickler.D")))
    )(identity)
  }

  it should "find schema for map of generic products" in {
    val schema = implicitlySchema[Map[String, H[D]]]
    schema.name shouldBe Some(SName("Map", List("sttp.tapir.json.pickler.H", "sttp.tapir.json.pickler.D")))
    schema.schemaType shouldBe SOpenProduct[Map[String, H[D]], H[D]](
      Nil,
      Schema(
        SProduct[H[D]](
          List(
            field(
              FieldName("data"),
              Schema(SProduct[D](List(field(FieldName("someFieldName"), stringSchema))), Some(SName("sttp.tapir.json.pickler.D")))
            )
          )
        ),
        Some(SName("sttp.tapir.json.pickler.H", List("sttp.tapir.json.pickler.D")))
      )
    )(identity)
  }

  it should "Not propagate type encodedName to subtypes of a sealed trait, but keep inheritance for fields" in {
    val parentSchema = implicitlySchema[Hericium]
    val child1Schema = implicitlySchema[Hericium.Erinaceus]
    val child2Schema = implicitlySchema[Hericium.Botryoides]

    parentSchema.name.map(_.fullName) shouldBe Some("CustomHericium")
    parentSchema.schemaType.asInstanceOf[SCoproduct[Hericium]].subtypes.flatMap(_.name.map(_.fullName)) should contain allOf (
      "sttp.tapir.SchemaMacroTestData.Hericium.Abietis", "sttp.tapir.SchemaMacroTestData.Hericium.Botryoides", "CustomErinaceus"
    )
    child1Schema.name.map(_.fullName) shouldBe Some("CustomErinaceus")
    child2Schema.name.map(_.fullName) shouldBe Some("sttp.tapir.SchemaMacroTestData.Hericium.Botryoides")
    inside(child2Schema.schemaType.asInstanceOf[SProduct[Hericium.Botryoides]].fields.find(_.name.encodedName == "customCommonField")) {
      case Some(field) =>
        field.schema.name.map(_.fullName) shouldBe None
        field.schema.description shouldBe Some("A common field")
    }
  }

  ignore should "add meta-data to schema from annotations" in { // TODO https://github.com/softwaremill/tapir/issues/3167
    val schema = implicitlySchema[I]
    schema shouldBe Schema[I](
      SProduct(
        List(
          field(
            FieldName("int"),
            intSchema.description("some int field").format("int32").default(1234).encodedExample(1234).validate(Validator.max(100))
          ),
          field(FieldName("noDesc"), longSchema),
          field(
            FieldName("bool", "alternativeBooleanName"),
            implicitlySchema[Option[Boolean]].description("another optional boolean flag")
          ),
          field(
            FieldName("child", "child-k-name"),
            Schema[K](
              SProduct(
                List(
                  field(FieldName("double"), implicitlySchema[Double].format("double64")),
                  field(FieldName("str"), stringSchema.format("special-string"))
                )
              ),
              Some(SName("sttp.tapir.json.pickler.K"))
            ).deprecated(true).description("child-k-desc")
          )
        )
      ),
      Some(SName("sttp.tapir.json.pickler.I"))
    ).description(
      "class I"
    ) // TODO this causes test to fail, because SchemaDerivation doesn't support @description annotation on case classes
  }

  it should "find the right schema for a case class with simple types" in {
    // given
    case class Test1(
        f1: String,
        f2: Byte,
        f3: Short,
        f4: Int,
        f5: Long,
        f6: Float,
        f7: Double,
        f8: Boolean,
        f9: BigDecimal,
        f10: JBigDecimal,
        f11: JBigInteger
    )
    val schema = implicitlySchema[Test1]

    // when
    schema.name shouldBe Some(SName("sttp.tapir.json.pickler.SchemaGenericAutoTest.<local SchemaGenericAutoTest>.Test1"))
    schema.schemaType shouldBe SProduct[Test1](
      List(
        field(FieldName("f1"), implicitlySchema[String]),
        field(FieldName("f2"), implicitlySchema[Byte]),
        field(FieldName("f3"), implicitlySchema[Short]),
        field(FieldName("f4"), implicitlySchema[Int]),
        field(FieldName("f5"), implicitlySchema[Long]),
        field(FieldName("f6"), implicitlySchema[Float]),
        field(FieldName("f7"), implicitlySchema[Double]),
        field(FieldName("f8"), implicitlySchema[Boolean]),
        field(FieldName("f9"), implicitlySchema[BigDecimal]),
        field(FieldName("f10"), implicitlySchema[JBigDecimal]),
        field(FieldName("f11"), implicitlySchema[JBigInteger])
      )
    )
  }

  it should "find schema for a simple case class and use snake case naming transformation" in {
    val expectedSnakeCaseNaming =
      expectedDSchema.copy(fields = List(field[D, String](FieldName("someFieldName", "some_field_name"), stringSchema)))
    implicit val customConf: PicklerConfiguration = PicklerConfiguration.default.withSnakeCaseMemberNames
    implicitlySchema[D].schemaType shouldBe expectedSnakeCaseNaming
  }

  it should "find schema for a simple case class and use screaming snake case naming transformation" in {
    val expectedScreamingSnakeCaseNaming =
      expectedDSchema.copy(fields = List(field[D, String](FieldName("someFieldName", "SOME_FIELD_NAME"), stringSchema)))
    implicit val customConf: PicklerConfiguration = PicklerConfiguration.default.withScreamingSnakeCaseMemberNames
    implicitlySchema[D].schemaType shouldBe expectedScreamingSnakeCaseNaming
  }

  it should "find schema for a simple case class and use kebab case naming transformation" in {
    val expectedKebabCaseNaming =
      expectedDSchema.copy(fields = List(field[D, String](FieldName("someFieldName", "some-field-name"), stringSchema)))
    implicit val customConf: PicklerConfiguration = PicklerConfiguration.default.withKebabCaseMemberNames
    implicitlySchema[D].schemaType shouldBe expectedKebabCaseNaming
  }

  it should "not transform names which are annotated with a custom name" in {
    implicit val customConf: PicklerConfiguration = PicklerConfiguration.default.withSnakeCaseMemberNames
    val schema = implicitlySchema[L]
    schema shouldBe Schema[L](
      SProduct(
        List(
          field(FieldName("firstField", "specialName"), intSchema),
          field(FieldName("secondField", "second_field"), intSchema)
        )
      ),
      Some(SName("sttp.tapir.json.pickler.L"))
    )
  }

  ignore should "customise the schema using the given function" in { // TODO https://github.com/softwaremill/tapir/issues/3166
    val schema = implicitlySchema[M]
    schema.attribute(M.testAttributeKey) shouldBe Some("test")
  }

  it should "generate one-of schema using the given discriminator" in {
    implicit val customConf: PicklerConfiguration = PicklerConfiguration.default.withDiscriminator("who_am_i")
    val schemaType = implicitlySchema[Entity].schemaType
    schemaType shouldBe a[SCoproduct[Entity]]

    schemaType.asInstanceOf[SCoproduct[Entity]].subtypes should contain theSameElementsAs List(
      Schema(
        SProduct[Organization](
          List(field(FieldName("name"), Schema(SString())), field(FieldName("who_am_i"), Schema(SString())))
        ),
        Some(SName("sttp.tapir.json.pickler.Organization"))
      ),
      Schema(
        SProduct[Person](
          List(
            field(FieldName("first"), Schema(SString())),
            field(FieldName("age"), Schema(SInteger(), format = Some("int32"))),
            field(FieldName("who_am_i"), Schema(SString()))
          )
        ),
        Some(SName("sttp.tapir.json.pickler.Person"))
      ),
      Schema(
        SProduct[UnknownEntity.type](
          List(
            field(FieldName("who_am_i"), Schema(SString()))
          )
        ),
        Some(SName("sttp.tapir.json.pickler.UnknownEntity"))
      )
    )

    schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "Organization" -> SRef(SName("sttp.tapir.json.pickler.Organization")),
          "Person" -> SRef(SName("sttp.tapir.json.pickler.Person")),
          "UnknownEntity" -> SRef(SName("sttp.tapir.json.pickler.UnknownEntity"))
        )
      )
    )
  }

  it should "generate default coproduct schema with a $type discriminator" in {
    implicit val customConf: PicklerConfiguration = PicklerConfiguration.default
    implicitlySchema[Entity].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("$type"),
        Map(
          "Organization" -> SRef(SName("sttp.tapir.json.pickler.Organization")),
          "Person" -> SRef(SName("sttp.tapir.json.pickler.Person")),
          "UnknownEntity" -> SRef(SName("sttp.tapir.json.pickler.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (kebab case subtype names)" in {
    implicit val customConf: PicklerConfiguration =
      PicklerConfiguration.default.withDiscriminator("who_am_i").withKebabCaseDiscriminatorValues
    implicitlySchema[Entity].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "organization" -> SRef(SName("sttp.tapir.json.pickler.Organization")),
          "person" -> SRef(SName("sttp.tapir.json.pickler.Person")),
          "unknown-entity" -> SRef(SName("sttp.tapir.json.pickler.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (snake case subtype names)" in {
    implicit val customConf: PicklerConfiguration =
      PicklerConfiguration.default.withDiscriminator("who_am_i").withSnakeCaseDiscriminatorValues
    implicitlySchema[Entity].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "organization" -> SRef(SName("sttp.tapir.json.pickler.Organization")),
          "person" -> SRef(SName("sttp.tapir.json.pickler.Person")),
          "unknown_entity" -> SRef(SName("sttp.tapir.json.pickler.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (full subtype names)" in {
    implicit val customConf: PicklerConfiguration = PicklerConfiguration.default.withDiscriminator("who_am_i").withFullDiscriminatorValues
    implicitlySchema[Entity].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "sttp.tapir.json.pickler.Organization" -> SRef(SName("sttp.tapir.json.pickler.Organization")),
          "sttp.tapir.json.pickler.Person" -> SRef(SName("sttp.tapir.json.pickler.Person")),
          "sttp.tapir.json.pickler.UnknownEntity" -> SRef(SName("sttp.tapir.json.pickler.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (full kebab case subtype names)" in {
    implicit val customConf: PicklerConfiguration =
      PicklerConfiguration.default.withDiscriminator("who_am_i").withFullKebabCaseDiscriminatorValues
    implicitlySchema[Entity].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "sttp.tapir.json.pickler.organization" -> SRef(SName("sttp.tapir.json.pickler.Organization")),
          "sttp.tapir.json.pickler.person" -> SRef(SName("sttp.tapir.json.pickler.Person")),
          "sttp.tapir.json.pickler.unknown-entity" -> SRef(SName("sttp.tapir.json.pickler.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (full snake case subtype names)" in {
    implicit val customConf: PicklerConfiguration =
      PicklerConfiguration.default.withDiscriminator("who_am_i").withFullSnakeCaseDiscriminatorValues
    implicitlySchema[Entity].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "sttp.tapir.json.pickler.organization" -> SRef(SName("sttp.tapir.json.pickler.Organization")),
          "sttp.tapir.json.pickler.person" -> SRef(SName("sttp.tapir.json.pickler.Person")),
          "sttp.tapir.json.pickler.unknown_entity" -> SRef(SName("sttp.tapir.json.pickler.UnknownEntity"))
        )
      )
    )
  }

  it should "find schema for subtypes containing parent metadata from annotations" in {
    val schemaType = implicitlySchema[Pet].schemaType

    val expectedCatSchema = Schema(
      SProduct[Cat](
        List(
          field(FieldName("name"), stringSchema.copy(description = Some("cat name"))),
          field(FieldName("catFood"), stringSchema.copy(description = Some("cat food"))),
          field(FieldName("$type"), Schema(SString()))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Cat"))
    )

    val expectedDogSchema = Schema(
      SProduct[Dog](
        List(
          field(FieldName("name"), stringSchema.copy(description = Some("name"))),
          field(FieldName("dogFood"), stringSchema.copy(description = Some("dog food"))),
          field(FieldName("$type"), Schema(SString()))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Dog"))
    )

    val expectedHamsterSchema = Schema(
      SProduct[Hamster](
        List(
          field(FieldName("name"), stringSchema.copy(description = Some("name"))),
          field(FieldName("likesNuts"), booleanSchema.copy(description = Some("likes nuts?"))),
          field(FieldName("$type"), Schema(SString()))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Hamster"))
    )

    schemaType.asInstanceOf[SCoproduct[Pet]].subtypes should contain allOf (expectedCatSchema, expectedDogSchema, expectedHamsterSchema)
  }

  it should "add validators for collection and option elements" in {
    case class ValidateEachTest(
        @validateEach(Validator.min(5))
        ints: List[Int],
        @validateEach[String](Validator.minLength(3))
        maybeString: Option[String]
    )

    val schema = implicitlySchema[ValidateEachTest]
    schema.applyValidation(ValidateEachTest(Nil, None)) should have size 0
    schema.applyValidation(ValidateEachTest(List(6, 10), Some("1234"))) should have size 0
    schema.applyValidation(ValidateEachTest(List(6, 0, 10), Some("1234"))) should have size 1
    schema.applyValidation(ValidateEachTest(List(6, 10), Some("12"))) should have size 1
  }
}

object SchemaGenericAutoTest {
  import generic.auto._
  def implicitlySchema[A: Pickler]: Schema[A] = summon[Pickler[A]].schema

  private[json] val stringSchema = implicitlySchema[String]
  private[json] val intSchema = implicitlySchema[Int]
  private[json] val longSchema = implicitlySchema[Long]
  private[json] val booleanSchema = implicitlySchema[Boolean]

  val expectedDSchema: SProduct[D] =
    SProduct[D](List(field(FieldName("someFieldName"), stringSchema)))

  // comparing recursive schemas without validators
  private[json] def removeValidators[T](s: Schema[T]): Schema[T] = (s.schemaType match {
    case SProduct(fields) => s.copy(schemaType = SProduct(convertToSProductField(fields)))
    case st @ SCoproduct(subtypes, discriminator) =>
      s.copy(schemaType =
        SCoproduct(
          subtypes.map(subtypeSchema => removeValidators(subtypeSchema)),
          discriminator
        )(st.subtypeSchema)
      )
    case st @ SOpenProduct(fields, valueSchema) =>
      s.copy(schemaType =
        SOpenProduct(
          fields = convertToSProductField(fields),
          valueSchema = removeValidators(valueSchema)
        )(st.mapFieldValues)
      )
    case st @ SArray(element)  => s.copy(schemaType = SArray(removeValidators(element))(st.toIterable))
    case st @ SOption(element) => s.copy(schemaType = SOption(removeValidators(element))(st.toOption))
    case _                     => s
  }).copy(validator = Validator.pass)

  private def convertToSProductField[T](fields: List[SProductField[T]]) = {
    fields.map(f => SProductField[T, f.FieldType](f.name, removeValidators(f.schema), f.get))
  }
}

case class StringValueClass(value: String) extends AnyVal
case class IntegerValueClass(value: Int) extends AnyVal

case class A(f1: String, f2: Int, f3: Option[String])
case class B(g1: String, g2: A)
case class C(h1: List[String], h2: Option[Int])
case class D(someFieldName: String)
case class F(f1: List[F], f2: Int)

class Custom(c: String)
case class G(f1: Int, f2: Custom)

case class H[T](data: T)

@description("class I")
case class I(
    @description("some int field")
    @default(1234)
    @encodedExample(1234)
    @format("int32")
    @validate[Int](Validator.max(100))
    int: Int,
    noDesc: Long,
    @description("another optional boolean flag")
    @encodedName("alternativeBooleanName")
    bool: Option[Boolean],
    @deprecated
    @description("child-k-desc")
    @encodedName("child-k-name")
    child: K
)

case class K(
    @format("double64")
    double: Double,
    @format("special-string")
    str: String
)

case class L(
    @encodedName("specialName")
    firstField: Int,
    secondField: Int
)

@customise(s => s.attribute(M.testAttributeKey, "test"))
case class M(field: Int)
object M {
  val testAttributeKey: AttributeKey[String] = AttributeKey[String]
}

sealed trait Node
case class Edge(id: Long, source: Node) extends Node
case class SimpleNode(id: Long) extends Node

case class IOpt(i1: Option[IOpt], i2: Int)
case class JOpt(data: Option[IOpt])

case class IList(i1: List[IList], i2: Int)
case class JList(data: List[IList])

sealed trait Entity
case class Person(first: String, age: Int) extends Entity
case class Organization(name: String) extends Entity
case object UnknownEntity extends Entity
