package sttp.tapir.docs.apispec.schema

import io.circe.literal._
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.circe._
import sttp.apispec.{Schema => ASchema}
import sttp.apispec.{SchemaType => ASchemaType}
import sttp.tapir._
import sttp.tapir.docs.apispec.schema.SchemaId
import sttp.tapir.generic.auto._

import scala.collection.immutable.ListMap

class JsonSchemasTest extends AnyFlatSpec with Matchers with OptionValues with EitherValues with Inside {
  behavior of "JsonSchemas"

  it should "Represent schema as JSON" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childNames: List[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = JsonSchemas(List(tSchema), markOptionsAsNullable = true).schemas.values

    // then
    result.asJson.deepDropNullValues shouldBe json"""[{"required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/components/schemas/Child"}}},{"type":"object","properties":{"childNames":{"type":"array","items":{"type":"string"}}}}]"""
  }

  it should "Handle top-level simple schemas" in {
    // given
    val tSchema1 = implicitly[Schema[List[Int]]]
    val tSchema2 = implicitly[Schema[Double]]

    // when
    val result = JsonSchemas(List(tSchema1, tSchema2), markOptionsAsNullable = true).schemas.values

    // then
    result.asJson.deepDropNullValues shouldBe json"""[{"type":"array","items":{"type":"integer","format":"int32"}},{"type":"number","format":"double"}]"""
  }

  it should "Add metaschemas to top-level simple schemas" in {
    // given
    val tSchema1 = implicitly[Schema[List[Int]]]
    val tSchema2 = implicitly[Schema[Double]]

    // when
    val result = JsonSchemas(List(tSchema1, tSchema2), markOptionsAsNullable = true, metaSchema = Some(MetaSchema202012)).schemas.values

    // then
    result.asJson.deepDropNullValues shouldBe json"""[{"$$schema":"https://json-schema.org/draft/2020-12/schema","type":"array","items":{"type":"integer","format":"int32"}},{"$$schema":"https://json-schema.org/draft/2020-12/schema","type":"number","format":"double"}]"""
  }

  it should "Add metaschema to class schemas" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: String)
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = JsonSchemas(List(tSchema), markOptionsAsNullable = true, metaSchema = Some(MetaSchema202012)).schemas.values

    // then
    result.size shouldBe (2)
    result.asJson.deepDropNullValues shouldBe json"""[{"$$schema":"https://json-schema.org/draft/2020-12/schema","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/components/schemas/Child"}}},{"$$schema":"https://json-schema.org/draft/2020-12/schema","required":["childName"],"type":"object","properties":{"childName":{"type":"string"}}}]"""

  }

  it should "convert a schema" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: String)
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result: ListMap[SchemaId, ASchema] = JsonSchemas(List(tSchema), markOptionsAsNullable = false).schemas

    // then
    result.size shouldBe (2)
    val parentSchema: ASchema = result.get("Parent").value
    parentSchema.required shouldBe List("innerChildField")
    parentSchema.properties.get("innerChildField").value.left.value.$ref shouldBe ("#/components/schemas/Child")
    val childSchema: ASchema = result.get("Child").value
    childSchema.required shouldBe List("childName")
    inside(childSchema.properties.get("childName").value.value) { case schema: ASchema =>
      schema.`type` shouldBe Some(ASchemaType.String)
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
    val result = JsonSchemas(List(tSchema), markOptionsAsNullable = false).schemas

    // then
    result.size shouldBe (3)
    val parentSchema: ASchema = result.get("Parent").value
    parentSchema.properties.get("innerChildField").value.left.value.$ref shouldBe ("#/components/schemas/Child")
    parentSchema.properties.get("childDetails").value.left.value.$ref shouldBe ("#/components/schemas/Child1")
    val childDetailsSchema: ASchema = result.get("Child1").value
    childDetailsSchema.required shouldBe List("age", "height")
  }

  it should "handle options as not nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = JsonSchemas(List(tSchema), markOptionsAsNullable = false).schemas

    // then
    result.size shouldBe (2)
    val childSchema: ASchema = result.get("Child").value
    childSchema.required shouldBe List.empty
    inside(childSchema.properties.get("childName").value.value) { case schema: ASchema =>
      schema.nullable shouldBe None
    }
  }

  it should "handle options as nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = JsonSchemas(List(tSchema), markOptionsAsNullable = true).schemas

    // then
    result.size shouldBe (2)
    val childSchema: ASchema = result.get("Child").value
    childSchema.required shouldBe List.empty
    inside(childSchema.properties.get("childName").value.value) { case schema: ASchema =>
      schema.nullable shouldBe Some(true)
    }
  }
}
