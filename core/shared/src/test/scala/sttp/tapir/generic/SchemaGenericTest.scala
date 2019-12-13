package sttp.tapir.generic

import java.math.{BigDecimal => JBigDecimal}

import com.github.ghik.silencer.silent
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.SchemaType._
import sttp.tapir.{SchemaType, Schema}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Future}

@silent("never used")
class SchemaGenericTest extends FlatSpec with Matchers {
  private val stringSchema = implicitly[Schema[String]]
  private val intSchema = implicitly[Schema[Int]]
  private val longSchema = implicitly[Schema[Long]]

  "Schema" should "find schema for simple types" in {
    stringSchema.schemaType shouldBe SString
    stringSchema.isOptional shouldBe false

    implicitly[Schema[Short]].schemaType shouldBe SInteger
    intSchema.schemaType shouldBe SInteger
    longSchema.schemaType shouldBe SInteger
    implicitly[Schema[Float]].schemaType shouldBe SNumber
    implicitly[Schema[Double]].schemaType shouldBe SNumber
    implicitly[Schema[Boolean]].schemaType shouldBe SBoolean
    implicitly[Schema[BigDecimal]].schemaType shouldBe SString
    implicitly[Schema[JBigDecimal]].schemaType shouldBe SString
  }

  it should "find schema for value classes" in {
    implicitly[Schema[StringValueClass]].schemaType shouldBe SString
    implicitly[Schema[IntegerValueClass]].schemaType shouldBe SInteger
  }

  it should "find schema for collections of value classes" in {
    implicitly[Schema[Array[StringValueClass]]].schemaType shouldBe SArray(stringSchema)
    implicitly[Schema[Array[IntegerValueClass]]].schemaType shouldBe SArray(intSchema)
  }

  it should "find schema for optional types" in {
    implicitly[Schema[Option[String]]].schemaType shouldBe SString
    implicitly[Schema[Option[String]]].isOptional shouldBe true
  }

  it should "find schema for collections" in {
    implicitly[Schema[Array[String]]].schemaType shouldBe SArray(stringSchema)
    implicitly[Schema[Array[String]]].isOptional shouldBe true

    implicitly[Schema[List[String]]].schemaType shouldBe SArray(stringSchema)
    implicitly[Schema[List[String]]].isOptional shouldBe true

    implicitly[Schema[Set[String]]].schemaType shouldBe SArray(stringSchema)
    implicitly[Schema[List[String]]].isOptional shouldBe true
  }

  val expectedASchema: Schema[A] =
    Schema[A](
      SProduct(
        SObjectInfo("sttp.tapir.generic.A"),
        List(("f1", stringSchema), ("f2", intSchema), ("f3", stringSchema.asOptional))
      )
    )

  it should "find schema for collections of case classes" in {
    implicitly[Schema[List[A]]].schemaType shouldBe SArray(expectedASchema)
  }

  it should "find schema for a simple case class" in {
    implicitly[Schema[A]] shouldBe expectedASchema
    implicitly[Schema[A]].schemaType.asInstanceOf[SProduct].required shouldBe List("f1", "f2")
  }

  val expectedDSchema: SProduct = SProduct(SObjectInfo("sttp.tapir.generic.D"), List(("someFieldName", stringSchema)))

  it should "find schema for a simple case class and use identity naming transformation" in {
    implicitly[Schema[D]].schemaType shouldBe expectedDSchema
  }

  it should "find schema for a simple case class and use snake case naming transformation" in {
    val expectedSnakeCaseNaming = expectedDSchema.copy(fields = List(("some_field_name", stringSchema)))
    implicit val customConf: Configuration = Configuration.default.withSnakeCaseMemberNames
    implicitly[Schema[D]].schemaType shouldBe expectedSnakeCaseNaming
  }

  it should "find schema for a simple case class and use kebab case naming transformation" in {
    val expectedKebabCaseNaming = expectedDSchema.copy(fields = List(("some-field-name", stringSchema)))
    implicit val customConf: Configuration = Configuration.default.withKebabCaseMemberNames
    implicitly[Schema[D]].schemaType shouldBe expectedKebabCaseNaming
  }

  it should "find schema for a nested case class" in {
    implicitly[Schema[B]].schemaType shouldBe SProduct(
      SObjectInfo("sttp.tapir.generic.B"),
      List(("g1", stringSchema), ("g2", expectedASchema))
    )
  }

  it should "find schema for case classes with collections" in {
    implicitly[Schema[C]].schemaType shouldBe SProduct(
      SObjectInfo("sttp.tapir.generic.C"),
      List(("h1", stringSchema.asArrayElement), ("h2", intSchema.asOptional))
    )
    implicitly[Schema[C]].schemaType.asInstanceOf[SProduct].required shouldBe Nil
  }

  it should "find schema for recursive data structure" in {
    val schema = implicitly[Schema[F]].schemaType
    schema shouldBe SProduct(
      SObjectInfo("sttp.tapir.generic.F"),
      List(("f1", Schema(SRef(SObjectInfo("sttp.tapir.generic.F"))).asArrayElement), ("f2", intSchema))
    )
  }

  it should "use custom schema for custom types" in {
    implicit val scustom: Schema[Custom] = Schema[Custom](SchemaType.SString)
    val schema = implicitly[Schema[G]].schemaType
    schema shouldBe SProduct(SObjectInfo("sttp.tapir.generic.G"), List(("f1", intSchema), ("f2", stringSchema)))
  }

  it should "derive schema for parametrised type classes" in {
    val schema = implicitly[Schema[H[A]]].schemaType
    schema shouldBe SProduct(SObjectInfo("sttp.tapir.generic.H", List("A")), List(("data", expectedASchema)))
  }

  it should "find schema for map" in {
    val schema = implicitly[Schema[Map[String, Int]]].schemaType
    schema shouldBe SOpenProduct(SObjectInfo("Map", List("Int")), intSchema)
  }

  it should "find schema for map of products" in {
    val schema = implicitly[Schema[Map[String, D]]].schemaType
    schema shouldBe SOpenProduct(
      SObjectInfo("Map", List("D")),
      Schema(SProduct(SObjectInfo("sttp.tapir.generic.D"), List(("someFieldName", stringSchema))))
    )
  }

  it should "find schema for map of generic products" in {
    val schema = implicitly[Schema[Map[String, H[D]]]].schemaType
    schema shouldBe SOpenProduct(
      SObjectInfo("Map", List("H", "D")),
      Schema(
        SProduct(
          SObjectInfo("sttp.tapir.generic.H", List("D")),
          List(("data", Schema(SProduct(SObjectInfo("sttp.tapir.generic.D"), List(("someFieldName", stringSchema))))))
        )
      )
    )
  }

  it should "find schema for map of value classes" in {
    val schema = implicitly[Schema[Map[String, IntegerValueClass]]].schemaType
    schema shouldBe SOpenProduct(SObjectInfo("Map", List("IntegerValueClass")), intSchema)
  }

  it should "find schema for recursive coproduct type" in {
    val schema = implicitly[Schema[Node]].schemaType
    schema shouldBe SCoproduct(
      SObjectInfo("sttp.tapir.generic.Node", List.empty),
      List(
        Schema(
          SProduct(
            SObjectInfo("sttp.tapir.generic.Edge"),
            List(
              "id" -> longSchema,
              "source" ->
                Schema(
                  SCoproduct(
                    SObjectInfo("sttp.tapir.generic.Node", List.empty),
                    List(
                      Schema(SRef(SObjectInfo("sttp.tapir.generic.Edge"))),
                      Schema(
                        SProduct(
                          SObjectInfo("sttp.tapir.generic.SimpleNode"),
                          List("id" -> longSchema)
                        )
                      )
                    ),
                    None
                  )
                )
            )
          )
        ),
        Schema(
          SProduct(
            SObjectInfo("sttp.tapir.generic.SimpleNode"),
            List("id" -> longSchema)
          )
        )
      ),
      None
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
    schema.schemaType shouldBe SProduct(
      SObjectInfo("sttp.tapir.generic.SchemaGenericTest.<local SchemaGenericTest>.Test1"),
      List(
        ("f1", implicitly[Schema[String]]),
        ("f2", implicitly[Schema[Byte]]),
        ("f3", implicitly[Schema[Short]]),
        ("f4", implicitly[Schema[Int]]),
        ("f5", implicitly[Schema[Long]]),
        ("f6", implicitly[Schema[Float]]),
        ("f7", implicitly[Schema[Double]]),
        ("f8", implicitly[Schema[Boolean]]),
        ("f9", implicitly[Schema[BigDecimal]]),
        ("f10", implicitly[Schema[JBigDecimal]])
      )
    )
  }

  it should "support derivation of recursive schemas wrapped with an option" in {
    // https://github.com/softwaremill/tapir/issues/192
    val expectedISchema: Schema[IOpt] =
      Schema(
        SProduct(
          SObjectInfo("sttp.tapir.generic.IOpt", List()),
          List(("i1", Schema(SRef(SObjectInfo("sttp.tapir.generic.IOpt")), isOptional = true)), ("i2", intSchema))
        )
      )
    val expectedJSchema: Schema[JOpt] = Schema(SProduct(SObjectInfo("sttp.tapir.generic.JOpt"), List(("data", expectedISchema.asOptional))))

    implicitly[Schema[IOpt]] shouldBe expectedISchema
    implicitly[Schema[JOpt]] shouldBe expectedJSchema
  }

  it should "support derivation of recursive schemas wrapped with a collection" in {
    val expectedISchema: Schema[IList] =
      Schema(
        SProduct(
          SObjectInfo("sttp.tapir.generic.IList", List()),
          List(("i1", Schema(SRef(SObjectInfo("sttp.tapir.generic.IList"))).asArrayElement), ("i2", intSchema))
        )
      )
    val expectedJSchema =
      Schema(SProduct(SObjectInfo("sttp.tapir.generic.JList"), List(("data", expectedISchema.asArrayElement))))

    implicitly[Schema[IList]] shouldBe expectedISchema
    implicitly[Schema[JList]] shouldBe expectedJSchema
  }
}

case class StringValueClass(value: String) extends AnyVal
case class IntegerValueClass(value: Int) extends AnyVal

case class A(f1: String, f2: Int, f3: Option[String])
case class B(g1: String, g2: A)
case class C(h1: List[String], h2: Option[Int])
case class D(someFieldName: String)
case class F(f1: List[F], f2: Int)

@silent("never used")
class Custom(c: String)
case class G(f1: Int, f2: Custom)

case class H[T](data: T)

sealed trait Node
case class Edge(id: Long, source: Node) extends Node
case class SimpleNode(id: Long) extends Node

case class IOpt(i1: Option[IOpt], i2: Int)
case class JOpt(data: Option[IOpt])

case class IList(i1: List[IList], i2: Int)
case class JList(data: List[IList])
