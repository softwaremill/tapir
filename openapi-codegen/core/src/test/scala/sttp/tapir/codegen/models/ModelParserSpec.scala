package sttp.tapir.codegen.openapi.models

import sttp.tapir.codegen.TestHelpers
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiResponse, OpenapiResponseContent}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{OpenapiSchemaArray, OpenapiSchemaRef, OpenapiSchemaString}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class ModelParserSpec extends AnyFlatSpec with Matchers with Checkers {
  import io.circe.yaml.parser
  import cats.implicits._
  import io.circe._

  it should "parse response yaml" in {
    val yaml = """
                 |'200':
                 |  description: ''
                 |  content:
                 |    application/json:
                 |      schema:
                 |        type: array
                 |        items:
                 |          $ref: '#/components/schemas/Book'
                 |default:
                 |  description: ''
                 |  content:
                 |    text/plain:
                 |      schema:
                 |        type: string""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[Seq[OpenapiResponse]])

    res shouldBe Right(
      Seq(
        OpenapiResponse(
          "200",
          "",
          Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaRef("#/components/schemas/Book"), false)))
        ),
        OpenapiResponse("default", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))
      )
    )

  }

  it should "parse bookstore yaml" in {
    val yaml = TestHelpers.myBookshopYaml

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    res shouldBe (Right(
      TestHelpers.myBookshopDoc
    ))
  }

  it should "parse bookstore yaml containing an endpoint with no parameters" in {
    val yaml = TestHelpers.generatedBookshopYaml

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])
      
      res shouldBe (Right(
        TestHelpers.generatedBookshopDoc
      ))
  }

}
