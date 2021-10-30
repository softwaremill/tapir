package codegen.openapi.models

import codegen.openapi.models.OpenapiModels.OpenapiResponseContent
import codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaInt, OpenapiSchemaObject, OpenapiSchemaString}
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
      .flatMap(_.as[OpenapiComponent])

    res shouldBe Right(
      OpenapiComponent(
        Map(
          "User" -> OpenapiSchemaObject(
            Map("id" -> OpenapiSchemaInt(false), "name" -> OpenapiSchemaString(false)),
            Seq("id", "name"),
            false
          )
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

}
