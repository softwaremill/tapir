package sttp.tapir.generic

import java.math.{BigDecimal => JBigDecimal}
import sttp.tapir.SchemaType._
import sttp.tapir.generic.auto._
import sttp.tapir.{
  FieldName,
  Schema,
  SchemaType,
  Validator,
  default,
  deprecated,
  description,
  encodedExample,
  encodedName,
  format,
  validate
}

import scala.concurrent.Future
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.TestUtil.field

class SchemaGenericAutoTest extends AsyncFlatSpec with Matchers {
  private val stringSchema = implicitly[Schema[String]]
  private val intSchema = implicitly[Schema[Int]]
  private val longSchema = implicitly[Schema[Long]]

  "Schema auto derivation" should "find schema for simple types" in {
    stringSchema.schemaType shouldBe SString()
    stringSchema.isOptional shouldBe false

    implicitly[Schema[Short]].schemaType shouldBe SInteger()
    intSchema.schemaType shouldBe SInteger()
    longSchema.schemaType shouldBe SInteger()
    implicitly[Schema[Float]].schemaType shouldBe SNumber()
    implicitly[Schema[Double]].schemaType shouldBe SNumber()
    implicitly[Schema[Boolean]].schemaType shouldBe SBoolean()
    implicitly[Schema[BigDecimal]].schemaType shouldBe SString()
    implicitly[Schema[JBigDecimal]].schemaType shouldBe SString()
  }

  it should "find schema for value classes" in {
    implicitly[Schema[StringValueClass]].schemaType shouldBe SString()
    implicitly[Schema[IntegerValueClass]].schemaType shouldBe SInteger()
  }

  it should "find schema for collections of value classes" in {
    implicitly[Schema[Array[StringValueClass]]].schemaType shouldBe SArray[Array[StringValueClass], StringValueClass](Schema(SString()))(
      _.toIterable
    )
    implicitly[Schema[Array[IntegerValueClass]]].schemaType shouldBe SArray[Array[IntegerValueClass], IntegerValueClass](
      Schema(SInteger())
    )(
      _.toIterable
    )
  }

  it should "find schema for optional types" in {
    implicitly[Schema[Option[String]]].schemaType shouldBe SOption[Option[String], String](Schema(SString()))(_.toIterable)
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
        SObjectInfo("sttp.tapir.generic.A"),
        List(field(FieldName("f1"), stringSchema), field(FieldName("f2"), intSchema), field(FieldName("f3"), stringSchema.asOption))
      )
    )

  it should "find schema for collections of case classes" in {
    implicitly[Schema[List[A]]].schemaType shouldBe SArray[List[A], A](expectedASchema)(_.toIterable)
  }

  it should "find schema for a simple case class" in {
    implicitly[Schema[A]] shouldBe expectedASchema
    implicitly[Schema[A]].schemaType.asInstanceOf[SProduct[A]].required shouldBe List(FieldName("f1"), FieldName("f2"))
  }

  val expectedDSchema: SProduct[D] =
    SProduct[D](SObjectInfo("sttp.tapir.generic.D"), List(field(FieldName("someFieldName"), stringSchema)))

  it should "find schema for a simple case class and use identity naming transformation" in {
    implicitly[Schema[D]].schemaType shouldBe expectedDSchema
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

  it should "find schema for a nested case class" in {
    implicitly[Schema[B]].schemaType shouldBe SProduct[B](
      SObjectInfo("sttp.tapir.generic.B"),
      List(field(FieldName("g1"), stringSchema), field(FieldName("g2"), expectedASchema))
    )
  }

  it should "find schema for case classes with collections" in {
    implicitly[Schema[C]].schemaType shouldBe SProduct[C](
      SObjectInfo("sttp.tapir.generic.C"),
      List(field(FieldName("h1"), stringSchema.asArray), field(FieldName("h2"), intSchema.asOption))
    )
    implicitly[Schema[C]].schemaType.asInstanceOf[SProduct[C]].required shouldBe Nil
  }

  it should "find schema for recursive data structure" in {
    val schema = removeValidators(implicitly[Schema[F]]).schemaType

    schema shouldBe SProduct[F](
      SObjectInfo("sttp.tapir.generic.F"),
      List(field(FieldName("f1"), Schema(SRef(SObjectInfo("sttp.tapir.generic.F"))).asArray), field(FieldName("f2"), intSchema))
    )
  }

  it should "find schema for recursive data structure when invoked from many threads" in {
    val expected =
      SProduct[F](
        SObjectInfo("sttp.tapir.generic.F"),
        List(field(FieldName("f1"), Schema(SRef(SObjectInfo("sttp.tapir.generic.F"))).asArray), field(FieldName("f2"), intSchema))
      )

    val count = 100
    val futures = (1 until count).map { _ =>
      Future[SchemaType[F]] {
        removeValidators(implicitly[Schema[F]]).schemaType
      }
    }

    val eventualSchemas = Future.sequence(futures)
    eventualSchemas.map { schemas =>
      schemas should contain only expected
    }
  }

  it should "use custom schema for custom types" in {
    implicit val scustom: Schema[Custom] = Schema[Custom](SchemaType.SString())
    val schema = implicitly[Schema[G]].schemaType
    schema shouldBe SProduct[G](
      SObjectInfo("sttp.tapir.generic.G"),
      List(field(FieldName("f1"), intSchema), field(FieldName("f2"), stringSchema))
    )
  }

  it should "derive schema for parametrised type classes" in {
    val schema = implicitly[Schema[H[A]]].schemaType
    schema shouldBe SProduct[H[A]](SObjectInfo("sttp.tapir.generic.H", List("A")), List(field(FieldName("data"), expectedASchema)))
  }

  it should "find schema for map" in {
    val schema = implicitly[Schema[Map[String, Int]]].schemaType
    schema shouldBe SOpenProduct[Map[String, Int], Int](SObjectInfo("Map", List("Int")), intSchema)(identity)
  }

  it should "find schema for map of products" in {
    val schema = implicitly[Schema[Map[String, D]]].schemaType
    schema shouldBe SOpenProduct[Map[String, D], D](
      SObjectInfo("Map", List("D")),
      Schema(SProduct(SObjectInfo("sttp.tapir.generic.D"), List(field(FieldName("someFieldName"), stringSchema))))
    )(identity)
  }

  it should "find schema for map of generic products" in {
    val schema = implicitly[Schema[Map[String, H[D]]]].schemaType
    schema shouldBe SOpenProduct[Map[String, H[D]], H[D]](
      SObjectInfo("Map", List("H", "D")),
      Schema(
        SProduct[H[D]](
          SObjectInfo("sttp.tapir.generic.H", List("D")),
          List(
            field(
              FieldName("data"),
              Schema(SProduct[D](SObjectInfo("sttp.tapir.generic.D"), List(field(FieldName("someFieldName"), stringSchema))))
            )
          )
        )
      )
    )(identity)
  }

  it should "add meta-data to schema from annotations" in {
    val schema = implicitly[Schema[I]]
    schema shouldBe Schema[I](
      SProduct(
        SObjectInfo("sttp.tapir.generic.I"),
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
                SObjectInfo("sttp.tapir.generic.K"),
                List(
                  field(FieldName("double"), implicitly[Schema[Double]].format("double64")),
                  field(FieldName("str"), stringSchema.format("special-string"))
                )
              )
            ).deprecated(true).description("child-k-desc")
          )
        )
      )
    ).description("class I")
  }

  it should "not transform names which are annotated with a custom name" in {
    implicit val customConf: Configuration = Configuration.default.withSnakeCaseMemberNames
    val schema = implicitly[Schema[L]]
    schema shouldBe Schema[L](
      SProduct(
        SObjectInfo("sttp.tapir.generic.L"),
        List(
          field(FieldName("firstField", "specialName"), intSchema),
          field(FieldName("secondField", "second_field"), intSchema)
        )
      )
    )
  }

  it should "find schema for map of value classes" in {
    val schema = implicitly[Schema[Map[String, IntegerValueClass]]].schemaType
    schema shouldBe SOpenProduct[Map[String, IntegerValueClass], IntegerValueClass](
      SObjectInfo("Map", List("IntegerValueClass")),
      Schema(SInteger())
    )(identity)
  }

  it should "find schema for recursive coproduct type" in {
    val schemaType = removeValidators(implicitly[Schema[Node]]).schemaType
    schemaType shouldBe a[SCoproduct[Node]]
    schemaType.asInstanceOf[SCoproduct[Node]].schemas.subtypes shouldBe Map(
      "sttp.tapir.generic.Edge" -> Schema(
        SProduct[Edge](
          SObjectInfo("sttp.tapir.generic.Edge"),
          List(
            field(FieldName("id"), longSchema),
            field(FieldName("source"), Schema(SRef(SObjectInfo("sttp.tapir.generic.Node", List.empty))))
          )
        )
      ),
      "sttp.tapir.generic.SimpleNode" -> Schema(
        SProduct[SimpleNode](
          SObjectInfo("sttp.tapir.generic.SimpleNode"),
          List(field(FieldName("id"), longSchema))
        )
      )
    )
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
    schema.schemaType shouldBe SProduct[Test1](
      SObjectInfo("sttp.tapir.generic.SchemaGenericAutoTest.<local SchemaGenericAutoTest>.Test1"),
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

  it should "support derivation of recursive schemas wrapped with an option" in {
    // https://github.com/softwaremill/tapir/issues/192
    val expectedISchema: Schema[IOpt] =
      Schema(
        SProduct(
          SObjectInfo("sttp.tapir.generic.IOpt", List()),
          List(
            field(FieldName("i1"), Schema(SRef(SObjectInfo("sttp.tapir.generic.IOpt"))).asOption),
            field(FieldName("i2"), intSchema)
          )
        )
      )
    val expectedJSchema: Schema[JOpt] =
      Schema(SProduct(SObjectInfo("sttp.tapir.generic.JOpt"), List(field(FieldName("data"), expectedISchema.asOption))))

    removeValidators(implicitly[Schema[IOpt]]) shouldBe expectedISchema
    removeValidators(implicitly[Schema[JOpt]]) shouldBe expectedJSchema
  }

  it should "support derivation of recursive schemas wrapped with a collection" in {
    val expectedISchema: Schema[IList] =
      Schema(
        SProduct(
          SObjectInfo("sttp.tapir.generic.IList", List()),
          List(
            field(FieldName("i1"), Schema(SRef(SObjectInfo("sttp.tapir.generic.IList"))).asArray),
            field(FieldName("i2"), intSchema)
          )
        )
      )
    val expectedJSchema =
      Schema(SProduct[JList](SObjectInfo("sttp.tapir.generic.JList"), List(field(FieldName("data"), expectedISchema.asArray))))

    removeValidators(implicitly[Schema[IList]]) shouldBe expectedISchema
    removeValidators(implicitly[Schema[JList]]) shouldBe expectedJSchema
  }

  it should "generate one-of schema using the given discriminator" in {
    implicit val customConf: Configuration = Configuration.default.withDiscriminator("who_am_i")

    val schemaType = implicitly[Schema[Entity]].schemaType
    schemaType shouldBe a[SCoproduct[Entity]]

    schemaType.asInstanceOf[SCoproduct[Entity]].schemas.subtypes shouldBe Map(
      "sttp.tapir.generic.Organization" -> Schema(
        SProduct[Organization](
          SObjectInfo("sttp.tapir.generic.Organization"),
          List(field(FieldName("name"), Schema(SString())), field(FieldName("who_am_i"), Schema(SString())))
        )
      ),
      "sttp.tapir.generic.Person" -> Schema(
        SProduct[Person](
          SObjectInfo("sttp.tapir.generic.Person"),
          List(
            field(FieldName("first"), Schema(SString())),
            field(FieldName("age"), Schema(SInteger())),
            field(FieldName("who_am_i"), Schema(SString()))
          )
        )
      )
    )

    schemaType.asInstanceOf[SCoproduct[Entity]].discriminator shouldBe Some(Discriminator("who_am_i", Map.empty))
  }

  // comparing recursive schemas without validators
  private def removeValidators[T](s: Schema[T]): Schema[T] = (s.schemaType match {
    case SProduct(info, fields) =>
      s.copy(schemaType =
        SProduct(
          info,
          fields.map(f =>
            new ProductField[T] {
              override type FieldType = f.FieldType
              override def name: FieldName = f.name
              override def get(t: T): Option[FieldType] = f.get(t)
              override def schema: Typeclass[FieldType] = removeValidators(f.schema)
            }
          )
        )
      )
    case SCoproduct(info, schemas, discriminator) =>
      s.copy(schemaType =
        SCoproduct(
          info,
          new SealedTrait[Schema, T] {
            override def dispatch(t: T): String = schemas.dispatch(t)
            override def subtypes: Map[String, Typeclass[T]] = schemas.subtypes.mapValues(removeValidators).toMap
          },
          discriminator
        )
      )
    case st @ SOpenProduct(info, valueSchema) => s.copy(schemaType = SOpenProduct(info, removeValidators(valueSchema))(st.fieldValues))
    case st @ SArray(element)                 => s.copy(schemaType = SArray(removeValidators(element))(st.toIterable))
    case st @ SOption(element)                => s.copy(schemaType = SOption(removeValidators(element))(st.toIterable))
    case _                                    => s
  }).copy(validator = Validator.pass)
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
