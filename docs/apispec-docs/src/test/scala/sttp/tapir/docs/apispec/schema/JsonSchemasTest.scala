package sttp.tapir.docs.apispec.schema

import io.circe.Printer
import io.circe.literal._
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.circe._
import sttp.apispec.{Schema => ASchema}
import sttp.tapir._
import sttp.tapir.generic.auto._

class JsonSchemasTest extends AnyFlatSpec with Matchers with OptionValues with EitherValues with Inside {
  behavior of "JsonSchemas"

  it should "Represent schema as JSON" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childId: String, childNames: List[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result: ASchema = JsonSchemas(tSchema.copy(description = Some("desc!")), markOptionsAsNullable = true)

    // then
    println(Printer.noSpaces.print(result.asJson.deepDropNullValues))
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"https://json-schema.org/draft-04/schema#","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}},"description":"desc!","$$defs":{"Child":{"required":["childId"],"type":"object","properties":{"childId":{"type":"string"},"childNames":{"type":"array","items":{"type":"string"}}}}}}"""

  }

  it should "Handle top-level simple schemas" in {
    // given
    val tSchema = implicitly[Schema[List[Int]]]

    // when
    val result = JsonSchemas(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"https://json-schema.org/draft-04/schema#","type":"array","items":{"type":"integer","format":"int32"}}"""
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
    val result = JsonSchemas(tSchema, markOptionsAsNullable = false)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"https://json-schema.org/draft-04/schema#","required":["innerChildField","childDetails"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"},"childDetails":{"$$ref":"#/$$defs/Child1"}},"$$defs":{"Child":{"required":["childName"],"type":"object","properties":{"childName":{"type":"string"}}},"Child1":{"required":["age","height"],"type":"object","properties":{"age":{"type":"integer","format":"int32"},"height":{"type":"integer","format":"int32"}}}}}"""
  }

  it should "handle options as not nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = JsonSchemas(tSchema, markOptionsAsNullable = false)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"https://json-schema.org/draft-04/schema#","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}},"$$defs":{"Child":{"type":"object","properties":{"childName":{"type":"string"}}}}}"""

  }

  it should "handle options as nullable" in {
    // given
    case class Parent(innerChildField: Child)
    case class Child(childName: Option[String])
    val tSchema = implicitly[Schema[Parent]]

    // when
    val result = JsonSchemas(tSchema, markOptionsAsNullable = true)

    // then
    result.asJson.deepDropNullValues shouldBe json"""{"$$schema":"https://json-schema.org/draft-04/schema#","required":["innerChildField"],"type":"object","properties":{"innerChildField":{"$$ref":"#/$$defs/Child"}},"$$defs":{"Child":{"type":"object","properties":{"childName":{"type":["string","null"]}}}}}"""
  }
}
