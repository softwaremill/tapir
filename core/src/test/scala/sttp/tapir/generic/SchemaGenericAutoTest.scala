package sttp.tapir.generic

import org.scalatest.Assertions
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.annotations._
import sttp.tapir.Schema.{SName, schemaForBoolean}
import sttp.tapir.SchemaMacroTestData.{Cat, Dog, Hamster, Pet}
import sttp.tapir.SchemaType._
import sttp.tapir.TestUtil.field
import sttp.tapir.generic.auto._
import sttp.tapir.{AttributeKey, FieldName, Schema, SchemaType, Validator}

import java.math.{BigDecimal => JBigDecimal}

class SchemaGenericAutoTest extends AsyncFlatSpec with Matchers {
  import SchemaGenericAutoTest._

  "Schema auto derivation" should "find schema for simple types" in {
    stringSchema.schemaType shouldBe SString()
    stringSchema.isOptional shouldBe false

    implicitly[Schema[Short]].schemaType shouldBe SInteger()
    intSchema.schemaType shouldBe SInteger()
    longSchema.schemaType shouldBe SInteger()
    implicitly[Schema[Float]].schemaType shouldBe SNumber()
    implicitly[Schema[Double]].schemaType shouldBe SNumber()
    implicitly[Schema[Boolean]].schemaType shouldBe SBoolean()
    implicitly[Schema[BigDecimal]].schemaType shouldBe SNumber()
    implicitly[Schema[JBigDecimal]].schemaType shouldBe SNumber()
  }

  it should "find schema for optional types" in {
    implicitly[Schema[Option[String]]].schemaType shouldBe SOption[Option[String], String](Schema(SString()))(identity)
    implicitly[Schema[Option[String]]].isOptional shouldBe true
  }

  it should "find schema for collections" in {
    implicitly[Schema[Array[String]]].schemaType shouldBe SArray[Array[String], String](stringSchema)(_.toIterable)
    implicitly[Schema[Array[String]]].isOptional shouldBe true

    implicitly[Schema[List[String]]].schemaType shouldBe SArray[List[String], String](stringSchema)(_.toIterable)
    implicitly[Schema[List[String]]].isOptional shouldBe true

    implicitly[Schema[Set[String]]].schemaType shouldBe SArray[Set[String], String](stringSchema)(_.toIterable)
    implicitly[Schema[Set[String]]].isOptional shouldBe true
  }

  val expectedASchema: Schema[A] =
    Schema[A](
      SProduct(
        List(field(FieldName("f1"), stringSchema), field(FieldName("f2"), intSchema), field(FieldName("f3"), stringSchema.asOption))
      ),
      Some(SName("sttp.tapir.generic.A"))
    )

  it should "find schema for collections of case classes" in {
    implicitly[Schema[List[A]]].schemaType shouldBe SArray[List[A], A](expectedASchema)(_.toIterable)
  }

  it should "find schema for a simple case class" in {
    implicitly[Schema[A]] shouldBe expectedASchema
    implicitly[Schema[A]].schemaType.asInstanceOf[SProduct[A]].required shouldBe List(FieldName("f1"), FieldName("f2"))
  }

  it should "find schema for a simple case class and use identity naming transformation" in {
    implicitly[Schema[D]].schemaType shouldBe expectedDSchema
  }

  it should "find schema for a nested case class" in {
    implicitly[Schema[B]].name shouldBe Some(SName("sttp.tapir.generic.B"))
    implicitly[Schema[B]].schemaType shouldBe SProduct[B](
      List(field(FieldName("g1"), stringSchema), field(FieldName("g2"), expectedASchema))
    )
  }

  it should "find schema for case classes with collections" in {
    implicitly[Schema[C]].name shouldBe Some(SName("sttp.tapir.generic.C"))
    implicitly[Schema[C]].schemaType shouldBe SProduct[C](
      List(field(FieldName("h1"), stringSchema.asArray), field(FieldName("h2"), intSchema.asOption))
    )
    implicitly[Schema[C]].schemaType.asInstanceOf[SProduct[C]].required shouldBe Nil
  }

  it should "use custom schema for custom types" in {
    implicit val scustom: Schema[Custom] = Schema[Custom](SchemaType.SString())
    val schema = implicitly[Schema[G]]
    schema.name shouldBe Some(SName("sttp.tapir.generic.G"))
    schema.schemaType shouldBe SProduct[G](
      List(field(FieldName("f1"), intSchema), field(FieldName("f2"), stringSchema))
    )
  }

  it should "derive schema for parametrised type classes" in {
    val schema = implicitly[Schema[H[A]]]
    schema.name shouldBe Some(SName("sttp.tapir.generic.H", List("sttp.tapir.generic.A")))
    schema.schemaType shouldBe SProduct[H[A]](List(field(FieldName("data"), expectedASchema)))
  }

  it should "find schema for map" in {
    val schema = implicitly[Schema[Map[String, Int]]]
    schema.name shouldBe Some(SName("Map", List("scala.Int")))
    schema.schemaType shouldBe SOpenProduct[Map[String, Int], Int](Nil, intSchema)(identity)
  }

  it should "find schema for map of products" in {
    val schema = implicitly[Schema[Map[String, D]]]
    schema.name shouldBe Some(SName("Map", List("sttp.tapir.generic.D")))
    schema.schemaType shouldBe SOpenProduct[Map[String, D], D](
      Nil,
      Schema(SProduct(List(field(FieldName("someFieldName"), stringSchema))), Some(SName("sttp.tapir.generic.D")))
    )(identity)
  }

  it should "find schema for map of generic products" in {
    val schema = implicitly[Schema[Map[String, H[D]]]]
    schema.name shouldBe Some(SName("Map", List("sttp.tapir.generic.H", "sttp.tapir.generic.D")))
    schema.schemaType shouldBe SOpenProduct[Map[String, H[D]], H[D]](
      Nil,
      Schema(
        SProduct[H[D]](
          List(
            field(
              FieldName("data"),
              Schema(SProduct[D](List(field(FieldName("someFieldName"), stringSchema))), Some(SName("sttp.tapir.generic.D")))
            )
          )
        ),
        Some(SName("sttp.tapir.generic.H", List("sttp.tapir.generic.D")))
      )
    )(identity)
  }

  it should "add meta-data to schema from annotations" in {
    val schema = implicitly[Schema[I]]
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
            implicitly[Schema[Option[Boolean]]].description("another optional boolean flag")
          ),
          field(
            FieldName("child", "child-k-name"),
            Schema[K](
              SProduct(
                List(
                  field(FieldName("double"), implicitly[Schema[Double]].format("double64")),
                  field(FieldName("str"), stringSchema.format("special-string"))
                )
              ),
              Some(SName("sttp.tapir.generic.K"))
            ).deprecated(true).description("child-k-desc")
          )
        )
      ),
      Some(SName("sttp.tapir.generic.I"))
    ).description("class I")
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
        f10: JBigDecimal
    )
    val schema = implicitly[Schema[Test1]]

    // when
    schema.name shouldBe Some(SName("sttp.tapir.generic.SchemaGenericAutoTest.<local SchemaGenericAutoTest>.Test1"))
    schema.schemaType shouldBe SProduct[Test1](
      List(
        field(FieldName("f1"), implicitly[Schema[String]]),
        field(FieldName("f2"), implicitly[Schema[Byte]]),
        field(FieldName("f3"), implicitly[Schema[Short]]),
        field(FieldName("f4"), implicitly[Schema[Int]]),
        field(FieldName("f5"), implicitly[Schema[Long]]),
        field(FieldName("f6"), implicitly[Schema[Float]]),
        field(FieldName("f7"), implicitly[Schema[Double]]),
        field(FieldName("f8"), implicitly[Schema[Boolean]]),
        field(FieldName("f9"), implicitly[Schema[BigDecimal]]),
        field(FieldName("f10"), implicitly[Schema[JBigDecimal]])
      )
    )
  }

  it should "find schema for a simple case class and use snake case naming transformation" in {
    val expectedSnakeCaseNaming =
      expectedDSchema.copy(fields = List(field[D, String](FieldName("someFieldName", "some_field_name"), stringSchema)))
    implicit val customConf: Configuration = Configuration.default.withSnakeCaseMemberNames
    implicitly[Schema[D]].schemaType shouldBe expectedSnakeCaseNaming
  }

  it should "find schema for a simple case class and use kebab case naming transformation" in {
    val expectedKebabCaseNaming =
      expectedDSchema.copy(fields = List(field[D, String](FieldName("someFieldName", "some-field-name"), stringSchema)))
    implicit val customConf: Configuration = Configuration.default.withKebabCaseMemberNames
    implicitly[Schema[D]].schemaType shouldBe expectedKebabCaseNaming
  }

  it should "not transform names which are annotated with a custom name" in {
    implicit val customConf: Configuration = Configuration.default.withSnakeCaseMemberNames
    val schema = implicitly[Schema[L]]
    schema shouldBe Schema[L](
      SProduct(
        List(
          field(FieldName("firstField", "specialName"), intSchema),
          field(FieldName("secondField", "second_field"), intSchema)
        )
      ),
      Some(SName("sttp.tapir.generic.L"))
    )
  }

  it should "customise the schema using the given function" in {
    val schema = implicitly[Schema[M]]
    schema.attribute(M.testAttributeKey) shouldBe Some("test")
  }

  it should "generate one-of schema using the given discriminator" in {
    implicit val customConf: Configuration = Configuration.default.withDiscriminator("who_am_i")
    val schemaType = implicitly[Schema[Entity]].schemaType
    schemaType shouldBe a[SCoproduct[Entity]]

    schemaType.asInstanceOf[SCoproduct[Entity]].subtypes should contain theSameElementsAs List(
      Schema(
        SProduct[Organization](
          List(field(FieldName("name"), Schema(SString())), field(FieldName("who_am_i"), Schema(SString())))
        ),
        Some(SName("sttp.tapir.generic.Organization"))
      ),
      Schema(
        SProduct[Person](
          List(
            field(FieldName("first"), Schema(SString())),
            field(FieldName("age"), Schema(SInteger(), format = Some("int32"))),
            field(FieldName("who_am_i"), Schema(SString()))
          )
        ),
        Some(SName("sttp.tapir.generic.Person"))
      ),
      Schema(
        SProduct[UnknownEntity.type](
          List(
            field(FieldName("who_am_i"), Schema(SString()))
          )
        ),
        Some(SName("sttp.tapir.generic.UnknownEntity"))
      )
    )

    schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "Organization" -> SRef(SName("sttp.tapir.generic.Organization")),
          "Person" -> SRef(SName("sttp.tapir.generic.Person")),
          "UnknownEntity" -> SRef(SName("sttp.tapir.generic.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (kebab case subtype names)" in {
    implicit val customConf: Configuration = Configuration.default.withDiscriminator("who_am_i").withKebabCaseDiscriminatorValues
    implicitly[Schema[Entity]].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "organization" -> SRef(SName("sttp.tapir.generic.Organization")),
          "person" -> SRef(SName("sttp.tapir.generic.Person")),
          "unknown-entity" -> SRef(SName("sttp.tapir.generic.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (snake case subtype names)" in {
    implicit val customConf: Configuration = Configuration.default.withDiscriminator("who_am_i").withSnakeCaseDiscriminatorValues
    implicitly[Schema[Entity]].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "organization" -> SRef(SName("sttp.tapir.generic.Organization")),
          "person" -> SRef(SName("sttp.tapir.generic.Person")),
          "unknown_entity" -> SRef(SName("sttp.tapir.generic.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (full subtype names)" in {
    implicit val customConf: Configuration = Configuration.default.withDiscriminator("who_am_i").withFullDiscriminatorValues
    implicitly[Schema[Entity]].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "sttp.tapir.generic.Organization" -> SRef(SName("sttp.tapir.generic.Organization")),
          "sttp.tapir.generic.Person" -> SRef(SName("sttp.tapir.generic.Person")),
          "sttp.tapir.generic.UnknownEntity" -> SRef(SName("sttp.tapir.generic.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (full kebab case subtype names)" in {
    implicit val customConf: Configuration = Configuration.default.withDiscriminator("who_am_i").withFullKebabCaseDiscriminatorValues
    implicitly[Schema[Entity]].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "sttp.tapir.generic.organization" -> SRef(SName("sttp.tapir.generic.Organization")),
          "sttp.tapir.generic.person" -> SRef(SName("sttp.tapir.generic.Person")),
          "sttp.tapir.generic.unknown-entity" -> SRef(SName("sttp.tapir.generic.UnknownEntity"))
        )
      )
    )
  }

  it should "generate one-of schema using the given discriminator (full snake case subtype names)" in {
    implicit val customConf: Configuration = Configuration.default.withDiscriminator("who_am_i").withFullSnakeCaseDiscriminatorValues
    implicitly[Schema[Entity]].schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(
      SDiscriminator(
        FieldName("who_am_i"),
        Map(
          "sttp.tapir.generic.organization" -> SRef(SName("sttp.tapir.generic.Organization")),
          "sttp.tapir.generic.person" -> SRef(SName("sttp.tapir.generic.Person")),
          "sttp.tapir.generic.unknown_entity" -> SRef(SName("sttp.tapir.generic.UnknownEntity"))
        )
      )
    )
  }

  it should "find schema for subtypes containing parent metadata from annotations" in {
    val schemaType = implicitly[Schema[Pet]].schemaType

    val expectedCatSchema = Schema(
      SProduct[Cat](
        List(
          field(FieldName("name"), stringSchema.copy(description = Some("cat name"))),
          field(FieldName("catFood"), stringSchema.copy(description = Some("cat food")))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Cat"))
    )

    val expectedDogSchema = Schema(
      SProduct[Dog](
        List(
          field(FieldName("name"), stringSchema.copy(description = Some("name"))),
          field(FieldName("dogFood"), stringSchema.copy(description = Some("dog food")))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Dog"))
    )

    val expectedHamsterSchema = Schema(
      SProduct[Hamster](
        List(
          field(FieldName("name"), stringSchema.copy(description = Some("name"))),
          field(FieldName("likesNuts"), booleanSchema.copy(description = Some("likes nuts?")))
        )
      ),
      Some(SName("sttp.tapir.SchemaMacroTestData.Hamster"))
    )

    val subtypes = schemaType.asInstanceOf[SCoproduct[Pet]].subtypes

    List(expectedCatSchema, expectedDogSchema, expectedHamsterSchema)
      .foldLeft(Assertions.succeed)((_, schema) => subtypes.contains(schema) shouldBe true)
  }

  it should "add validators for collection and option elements" in {
    case class ValidateEachTest(
        @validateEach(Validator.min(5))
        ints: List[Int],
        @validateEach[String](Validator.minLength(3))
        maybeString: Option[String]
    )

    val schema = implicitly[Schema[ValidateEachTest]]
    schema.applyValidation(ValidateEachTest(Nil, None)) should have size 0
    schema.applyValidation(ValidateEachTest(List(6, 10), Some("1234"))) should have size 0
    schema.applyValidation(ValidateEachTest(List(6, 0, 10), Some("1234"))) should have size 1
    schema.applyValidation(ValidateEachTest(List(6, 10), Some("12"))) should have size 1
  }
}

object SchemaGenericAutoTest {
  private[generic] val stringSchema = implicitly[Schema[String]]
  private[generic] val intSchema = implicitly[Schema[Int]]
  private[generic] val longSchema = implicitly[Schema[Long]]
  private[generic] val booleanSchema = implicitly[Schema[Boolean]]

  val expectedDSchema: SProduct[D] =
    SProduct[D](List(field(FieldName("someFieldName"), stringSchema)))

  // comparing recursive schemas without validators
  private[generic] def removeValidators[T](s: Schema[T]): Schema[T] = (s.schemaType match {
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
