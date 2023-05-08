package sttp.tapir.docs.apispec.schema

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import scala.collection.immutable.ListMap
import sttp.apispec.{Schema => ASchema}
import sttp.apispec.{SchemaType => ASchemaType}
import sttp.apispec.ReferenceOr
import sttp.tapir.docs.apispec.schema.SchemaId
import sttp.tapir.generic.auto._
import org.scalatest.OptionValues
import org.scalatest.EitherValues
import org.scalatest.Inside

class TapirSchemaToJsonSchemaTest extends AnyFlatSpec with Matchers with OptionValues with EitherValues with Inside {
  behavior of "TapirSchemaToJsonSchema"

  it should "convert a schema" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: String)
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result: ListMap[SchemaId, ReferenceOr[ASchema]] = TapirSchemaToJsonSchema(List(tSchema), markOptionsAsNullable = false)

    // then
    result.size shouldBe (2)
    val parentSchema: ASchema = result.get("Parent").value.value
    parentSchema.required shouldBe List("innerChildField")
    parentSchema.properties.get("innerChildField").value.left.value.$ref shouldBe ("#/components/schemas/Child")
    val childSchema: ASchema = result.get("Child").value.value
    childSchema.required shouldBe List("childName")
    inside (childSchema.properties.get("childName").value.value) {
      case schema: ASchema => schema.`type` shouldBe Some(ASchemaType.String)
    }
  }

  it should "handle repeated type names" in {
    // given
    object Childhood {
      case class Child(age: Int, height: Int)
    }
    case class Parent(innerChildField: Child, childDetails: Childhood.Child)
    case class Child(childName: String)
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = TapirSchemaToJsonSchema(List(tSchema), markOptionsAsNullable = false)

    // then
    result.size shouldBe (3)
    val parentSchema: ASchema = result.get("Parent").value.value
    parentSchema.properties.get("innerChildField").value.left.value.$ref shouldBe ("#/components/schemas/Child")
    parentSchema.properties.get("childDetails").value.left.value.$ref shouldBe ("#/components/schemas/Child1")
    val childDetailsSchema: ASchema = result.get("Child1").value.value
    childDetailsSchema.required shouldBe List("age", "height")
  }

  it should "handle options as not nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = TapirSchemaToJsonSchema(List(tSchema), markOptionsAsNullable = false)

    // then
    result.size shouldBe (2)
    val childSchema: ASchema = result.get("Child").value.value
    childSchema.required shouldBe List.empty
    inside (childSchema.properties.get("childName").value.value) {
      case schema: ASchema => schema.nullable shouldBe None
    }
  }

  it should "handle options as nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = TapirSchemaToJsonSchema(List(tSchema), markOptionsAsNullable = true)

    // then
    result.size shouldBe (2)
    val childSchema: ASchema = result.get("Child").value.value
    childSchema.required shouldBe List.empty
    inside (childSchema.properties.get("childName").value.value) {
      case schema: ASchema => schema.nullable shouldBe Some(true)
    }
  }
}
