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
import sttp.tapir.internal.IterableToListMap

class LegacySchemaGenericAutoTest extends AsyncFlatSpec with Matchers {
  import sttp.tapir.generic._
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
      Schema(SInteger())
    )(
      _.toIterable
    )
  }

  it should "find schema for map of value classes" in {
    val schema = implicitly[Schema[Map[String, IntegerValueClass]]].schemaType
    schema shouldBe SOpenProduct[Map[String, IntegerValueClass], IntegerValueClass](
      SObjectInfo("Map", List("IntegerValueClass")),
      Schema(SInteger())
    )(identity)
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

  it should "find schema for recursive coproduct type" in {
    val schemaType = removeValidators(implicitly[Schema[Node]]).schemaType
    schemaType shouldBe a[SCoproduct[Node]]
    schemaType.asInstanceOf[SCoproduct[Node]].subtypes shouldBe Map(
      SObjectInfo("sttp.tapir.generic.Edge") -> Schema(
        SProduct[Edge](
          SObjectInfo("sttp.tapir.generic.Edge"),
          List(
            field(FieldName("id"), longSchema),
            field(FieldName("source"), Schema(SRef(SObjectInfo("sttp.tapir.generic.Node", List.empty))))
          )
        )
      ),
      SObjectInfo("sttp.tapir.generic.SimpleNode") -> Schema(
        SProduct[SimpleNode](
          SObjectInfo("sttp.tapir.generic.SimpleNode"),
          List(field(FieldName("id"), longSchema))
        )
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

}
