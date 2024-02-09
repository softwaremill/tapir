package sttp.tapir.generic

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType._
import sttp.tapir.TestUtil.field
import sttp.tapir.generic.auto._
import sttp.tapir.{FieldName, Schema, SchemaType}

import scala.concurrent.Future

class LegacySchemaGenericAutoTest extends AsyncFlatSpec with Matchers {
  import sttp.tapir.generic.SchemaGenericAutoTest._

  it should "find schema for value classes" in {
    implicitly[Schema[StringValueClass]].schemaType shouldBe SString()
    implicitly[Schema[IntegerValueClass]].schemaType shouldBe SInteger()
  }

  it should "find schema for collections of value classes" in {
    implicitly[Schema[Array[StringValueClass]]].schemaType shouldBe SArray[Array[StringValueClass], StringValueClass](Schema(SString()))(
      _.toIterable
    )
    implicitly[Schema[Array[IntegerValueClass]]].schemaType shouldBe SArray[Array[IntegerValueClass], IntegerValueClass](
      Schema(SInteger(), format = Some("int32"))
    )(
      _.toIterable
    )
  }

  it should "find schema for map of value classes" in {
    val schema = implicitly[Schema[Map[String, IntegerValueClass]]]
    schema.name shouldBe Some(SName("Map", List("sttp.tapir.generic.IntegerValueClass")))
    schema.schemaType shouldBe SOpenProduct[Map[String, IntegerValueClass], IntegerValueClass](
      Nil,
      Schema(SInteger(), format = Some("int32"))
    )(
      identity
    )
  }

  it should "find schema for recursive data structure" in {
    val schema = removeValidators(implicitly[Schema[F]])

    schema.name shouldBe Some(SName("sttp.tapir.generic.F"))
    schema.schemaType shouldBe SProduct[F](
      List(field(FieldName("f1"), Schema(SRef(SName("sttp.tapir.generic.F"))).asArray), field(FieldName("f2"), intSchema))
    )
  }

  it should "find schema for recursive data structure when invoked from many threads" in {
    val expected =
      SProduct[F](
        List(field(FieldName("f1"), Schema(SRef(SName("sttp.tapir.generic.F"))).asArray), field(FieldName("f2"), intSchema))
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

  it should "find schema for recursive coproduct type" in {
    val schemaType = removeValidators(implicitly[Schema[Node]]).schemaType
    schemaType shouldBe a[SCoproduct[Node]]
    schemaType.asInstanceOf[SCoproduct[Node]].subtypes shouldBe List(
      Schema(
        SProduct[Edge](
          List(
            field(FieldName("id"), longSchema),
            field(FieldName("source"), Schema(SRef(SName("sttp.tapir.generic.Node", List.empty))))
          )
        ),
        Some(SName("sttp.tapir.generic.Edge"))
      ),
      Schema(
        SProduct[SimpleNode](
          List(field(FieldName("id"), longSchema))
        ),
        Some(SName("sttp.tapir.generic.SimpleNode"))
      )
    )
  }

  it should "support derivation of recursive schemas wrapped with an option" in {
    // https://github.com/softwaremill/tapir/issues/192
    val expectedISchema: Schema[IOpt] =
      Schema(
        SProduct(
          List(
            field(FieldName("i1"), Schema(SRef(SName("sttp.tapir.generic.IOpt"))).asOption),
            field(FieldName("i2"), intSchema)
          )
        ),
        Some(SName("sttp.tapir.generic.IOpt", List()))
      )
    val expectedJSchema: Schema[JOpt] =
      Schema(SProduct(List(field(FieldName("data"), expectedISchema.asOption))), Some(SName("sttp.tapir.generic.JOpt")))

    removeValidators(implicitly[Schema[IOpt]]) shouldBe expectedISchema
    removeValidators(implicitly[Schema[JOpt]]) shouldBe expectedJSchema
  }

  it should "support derivation of recursive schemas wrapped with a collection" in {
    val expectedISchema: Schema[IList] =
      Schema(
        SProduct(
          List(
            field(FieldName("i1"), Schema(SRef(SName("sttp.tapir.generic.IList"))).asArray),
            field(FieldName("i2"), intSchema)
          )
        ),
        Some(SName("sttp.tapir.generic.IList", List()))
      )
    val expectedJSchema =
      Schema(SProduct[JList](List(field(FieldName("data"), expectedISchema.asArray))), Some(SName("sttp.tapir.generic.JList")))

    removeValidators(implicitly[Schema[IList]]) shouldBe expectedISchema
    removeValidators(implicitly[Schema[JList]]) shouldBe expectedJSchema
  }
}
