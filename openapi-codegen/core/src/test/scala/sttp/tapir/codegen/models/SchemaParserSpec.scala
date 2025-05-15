package sttp.tapir.codegen.openapi.models

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiResponseContent
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  NumericRestrictions,
  OpenapiSchemaAny,
  OpenapiSchemaArray,
  OpenapiSchemaField,
  OpenapiSchemaInt,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaString
}
import sttp.tapir.codegen.openapi.models.OpenapiSecuritySchemeType.{
  OpenapiSecuritySchemeApiKeyType,
  OpenapiSecuritySchemeBasicType,
  OpenapiSecuritySchemeBearerType
}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class SchemaParserSpec extends AnyFlatSpec with Matchers with Checkers {
  import io.circe.yaml.parser
  import cats.implicits._
  import io.circe._

  it should "parse basic-structure (object) yaml" in {
    // https://swagger.io/docs/specification/basic-structure/
    val yaml = """
      |schemas:
      |  User:
      |    type: object
      |    properties:
      |      id:
      |        type: integer
      |      name:
      |        type: string
      |    # Both properties are required
      |    required:
      |      - id
      |      - name""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[Option[OpenapiComponent]])

    res shouldBe Right(
      Some(
        OpenapiComponent(
          Map(
            "User" -> OpenapiSchemaObject(
              Map(
                "id" -> OpenapiSchemaField(OpenapiSchemaInt(false, NumericRestrictions()), None),
                "name" -> OpenapiSchemaField(OpenapiSchemaString(false), None)
              ),
              Seq("id", "name"),
              false
            )
          )
        )
      )
    )
  }

  it should "parse map" in {
    val yaml = """
      |schemas:
      |  User:
      |    type: object
      |    properties:
      |      attributes:
      |        type: object
      |        additionalProperties:
      |          type: string
      |    required:
      |      - attributes""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiComponent])

    res shouldBe Right(
      OpenapiComponent(
        Map(
          "User" -> OpenapiSchemaObject(
            Map("attributes" -> OpenapiSchemaField(OpenapiSchemaMap(OpenapiSchemaString(false), false), None)),
            Seq("attributes"),
            false
          )
        )
      )
    )
  }

  it should "parse any type" in {
    val yaml = """
      |schemas:
      |  User:
      |    type: object
      |    properties:
      |      anyValue: {}
      |    required:
      |      - anyValue""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiComponent])

    res shouldBe Right(
      OpenapiComponent(
        Map(
          "User" -> OpenapiSchemaObject(
            Map("anyValue" -> OpenapiSchemaField(OpenapiSchemaAny(false), None)),
            Seq("anyValue"),
            false
          )
        )
      )
    )
  }

  it should "parse security schemes" in {
    val yaml =
      """
        |securitySchemes:
        |  httpAuthBearer:
        |    type: http
        |    scheme: bearer
        |  httpAuthBasic:
        |    type: http
        |    scheme: basic
        |  apiKeyHeader:
        |    type: apiKey
        |    in: header
        |    name: X-API-KEY
        |  apiKeyCookie:
        |    type: apiKey
        |    in: cookie
        |    name: api_key
        |  apiKeyQuery:
        |    type: apiKey
        |    in: query
        |    name: api-key""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiComponent])

    res shouldBe Right(
      OpenapiComponent(
        Map(),
        Map(
          "httpAuthBearer" -> OpenapiSecuritySchemeBearerType,
          "httpAuthBasic" -> OpenapiSecuritySchemeBasicType,
          "apiKeyHeader" -> OpenapiSecuritySchemeApiKeyType("header", "X-API-KEY"),
          "apiKeyCookie" -> OpenapiSecuritySchemeApiKeyType("cookie", "api_key"),
          "apiKeyQuery" -> OpenapiSecuritySchemeApiKeyType("query", "api-key")
        )
      )
    )
  }

  it should "parse basic-response (array) yaml" in {
    // https://swagger.io/docs/specification/basic-structure/
    val yaml = """application/json:
                 |  schema:
                 |    type: array
                 |    items:
                 |      type: string
                 |      """.stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[Seq[OpenapiResponseContent]])

    res shouldBe Right(Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaString(false), false))))
  }

  it should "parse array of object without properties yaml" in {
    // https://swagger.io/docs/specification/basic-structure/
    val yaml =
      """application/json:
        |  schema:
        |    type: array
        |    items:
        |      type: object
        |      """.stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[Seq[OpenapiResponseContent]])

    res shouldBe Right(
      Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaObject(Map.empty, Seq.empty, false), false)))
    )
  }

}
