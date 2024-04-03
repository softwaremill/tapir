package sttp.tapir.codegen.openapi.models

import sttp.tapir.codegen.TestHelpers
import sttp.tapir.codegen.openapi.models.OpenapiModels.{OpenapiDocument, OpenapiResponse, OpenapiResponseContent}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaConstantString,
  OpenapiSchemaEnum,
  OpenapiSchemaRef,
  OpenapiSchemaString,
  OpenapiSchemaUUID
}
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

  it should "parse hello yaml" in {
    val yaml = TestHelpers.helloYaml

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    res shouldBe (Right(
      TestHelpers.helloDocs
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

  it should "parse endpoint with single security entry" in {
    val res = parser
      .parse(TestHelpers.simpleSecurityYaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    res shouldBe (Right(
      TestHelpers.simpleSecurityDocs
    ))
  }

  it should "parse endpoint with complex security entry" in {
    val res = parser
      .parse(TestHelpers.complexSecurityYaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    res shouldBe (Right(
      TestHelpers.complexSecurityDocs
    ))
  }

  it should "parse uuids" in {
    val yaml =
      """
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
        |        type: string
        |        format: uuid""".stripMargin

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
        OpenapiResponse("default", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaUUID(false))))
      )
    )
  }

  it should "parse enums" in {
    val yaml =
      """type: string
        |enum:
        |- paperback
        |- hardback""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiSchemaType])

    res shouldBe Right(
      OpenapiSchemaEnum("string", Seq(OpenapiSchemaConstantString("paperback"), OpenapiSchemaConstantString("hardback")), false)
    )
  }

  it should "parse endpoint with defaults" in {
    val res = parser
      .parse(TestHelpers.withDefaultsYaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    res shouldBe (Right(
      TestHelpers.withDefaultsDocs
    ))
  }

  it should "parse endpoint with simple specification extensions" in {
    val res = parser
      .parse(TestHelpers.specificationExtensionYaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    res shouldBe (Right(
      TestHelpers.specificationExtensionDocs
    ))
  }

  it should "parse oneOf schemas" in {
    val res = parser
      .parse(TestHelpers.oneOfYaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])

    res shouldBe Right(
      TestHelpers.oneOfDocsWithMapping
    )
  }
}
