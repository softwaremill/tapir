package sttp.tapir.codegen.openapi.models

import sttp.tapir.codegen.TestHelpers
import sttp.tapir.codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiHeaderDef,
  OpenapiHeaderRef,
  OpenapiInfo,
  OpenapiParameter,
  OpenapiResponse,
  OpenapiResponseContent,
  OpenapiResponseDef
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaConstantString,
  OpenapiSchemaEnum,
  OpenapiSchemaRef,
  OpenapiSchemaString,
  OpenapiSchemaUUID
}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class ModelParserSpec extends AnyFlatSpec with Matchers with Checkers with EitherValues {
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
        OpenapiResponseDef(
          "200",
          "",
          Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaRef("#/components/schemas/Book"), false)))
        ),
        OpenapiResponseDef("default", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))
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
        OpenapiResponseDef(
          "200",
          "",
          Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaRef("#/components/schemas/Book"), false)))
        ),
        OpenapiResponseDef("default", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaUUID(false))))
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
    parser
      .parse(TestHelpers.enumQueryParamYaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument]) shouldBe Right(
      TestHelpers.enumQueryParamDocs
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

  it should "parse a components header described with content, without failing the document" in {
    val yaml = """
                 |schemas: {}
                 |headers:
                 |  X-Rate-Limit:
                 |    description: Requests left in the current window
                 |    content:
                 |      text/plain:
                 |        schema:
                 |          type: string""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiComponent])

    res.value.headers.keys.toList shouldBe List("#/components/headers/X-Rate-Limit")

    val thrown = intercept[IllegalStateException] {
      res.value.headers("#/components/headers/X-Rate-Limit").resolved("X-Rate-Limit", null)
    }
    thrown.getMessage should include("'content' instead of 'schema'")
  }

  it should "parse a components headers section, re-keyed by full ref" in {
    val yaml = """
                 |schemas: {}
                 |headers:
                 |  X-Rate-Limit:
                 |    description: Requests left in the current window
                 |    required: true
                 |    schema:
                 |      type: string""".stripMargin

    val res = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiComponent])

    res shouldBe Right(
      OpenapiComponent(
        schemas = Map.empty,
        headers = Map(
          "#/components/headers/X-Rate-Limit" -> OpenapiHeaderDef(
            OpenapiParameter("inline", "header", Some(true), Some("Requests left in the current window"), OpenapiSchemaString(false))
          )
        )
      )
    )
  }

  it should "resolve a response header ref against components.headers" in {
    val yaml = """
                 |openapi: 3.1.0
                 |info:
                 |  title: Rate limited
                 |  version: '1.0'
                 |paths:
                 |  /ping:
                 |    get:
                 |      operationId: getPing
                 |      responses:
                 |        '200':
                 |          description: ''
                 |          headers:
                 |            X-Rate-Limit:
                 |              $ref: '#/components/headers/RateLimit'
                 |          content:
                 |            text/plain:
                 |              schema:
                 |                type: string
                 |components:
                 |  schemas: {}
                 |  headers:
                 |    RateLimit:
                 |      description: Requests left in the current window
                 |      required: true
                 |      schema:
                 |        type: string""".stripMargin

    val doc = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])
      .value

    val response = doc.paths.head.methods.head.responses.head.asInstanceOf[OpenapiResponseDef]
    val (headerName, header) = response.getHeaders.head

    header.resolved(headerName, doc) shouldBe OpenapiHeaderDef(
      OpenapiParameter("X-Rate-Limit", "header", Some(true), Some("Requests left in the current window"), OpenapiSchemaString(false))
    )
  }

  it should "still resolve a response header ref against components.parameters" in {
    val doc = OpenapiDocument(
      "3.1.0",
      Nil,
      OpenapiInfo("Rate limited", "1.0"),
      Nil,
      Some(
        OpenapiComponent(
          schemas = Map.empty,
          parameters = Map(
            "#/components/parameters/RateLimit" ->
              OpenapiParameter("RateLimit", "header", Some(true), Some("Requests left"), OpenapiSchemaString(false))
          )
        )
      ),
      Nil
    )

    val ref = OpenapiHeaderRef(OpenapiSchemaRef("#/components/parameters/RateLimit"))

    ref.resolved("X-Rate-Limit", doc) shouldBe OpenapiHeaderDef(
      OpenapiParameter("X-Rate-Limit", "header", Some(true), Some("Requests left"), OpenapiSchemaString(false))
    )
  }

  it should "resolve a header ref whose key contains characters that are not legal in a scala name" in {
    val yaml = """
                 |openapi: 3.1.0
                 |info:
                 |  title: Rate limited
                 |  version: '1.0'
                 |paths:
                 |  /ping:
                 |    get:
                 |      operationId: getPing
                 |      responses:
                 |        '200':
                 |          description: ''
                 |          headers:
                 |            Retry-After:
                 |              $ref: '#/components/headers/Retry.After'
                 |components:
                 |  schemas: {}
                 |  headers:
                 |    Retry.After:
                 |      required: true
                 |      schema:
                 |        type: string""".stripMargin

    val doc = parser
      .parse(yaml)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])
      .value

    val response = doc.paths.head.methods.head.responses.head.asInstanceOf[OpenapiResponseDef]
    val (headerName, header) = response.getHeaders.head

    header.resolved(headerName, doc) shouldBe OpenapiHeaderDef(
      OpenapiParameter("Retry-After", "header", Some(true), None, OpenapiSchemaString(false))
    )
  }

  it should "resolve a header component which is itself a reference" in {
    val doc = OpenapiDocument(
      "3.1.0",
      Nil,
      OpenapiInfo("Rate limited", "1.0"),
      Nil,
      Some(
        OpenapiComponent(
          schemas = Map.empty,
          parameters = Map(
            "#/components/parameters/RateLimit" ->
              OpenapiParameter("RateLimit", "header", Some(true), Some("Requests left"), OpenapiSchemaString(false))
          ),
          headers = Map(
            "#/components/headers/Alias" -> OpenapiHeaderRef(OpenapiSchemaRef("#/components/parameters/RateLimit"))
          )
        )
      ),
      Nil
    )

    val ref = OpenapiHeaderRef(OpenapiSchemaRef("#/components/headers/Alias"))

    ref.resolved("X-Rate-Limit", doc) shouldBe OpenapiHeaderDef(
      OpenapiParameter("X-Rate-Limit", "header", Some(true), Some("Requests left"), OpenapiSchemaString(false))
    )
  }

  it should "fail with a clear message when header references form a cycle" in {
    val doc = OpenapiDocument(
      "3.1.0",
      Nil,
      OpenapiInfo("Rate limited", "1.0"),
      Nil,
      Some(
        OpenapiComponent(
          schemas = Map.empty,
          headers = Map(
            "#/components/headers/A" -> OpenapiHeaderRef(OpenapiSchemaRef("#/components/headers/B")),
            "#/components/headers/B" -> OpenapiHeaderRef(OpenapiSchemaRef("#/components/headers/A"))
          )
        )
      ),
      Nil
    )

    val ref = OpenapiHeaderRef(OpenapiSchemaRef("#/components/headers/A"))

    val thrown = intercept[IllegalStateException](ref.resolved("X-Rate-Limit", doc))
    thrown.getMessage should include("Circular header reference")
  }

  it should "fail with a clear message when a response header ref matches nothing" in {
    val doc = OpenapiDocument("3.1.0", Nil, OpenapiInfo("Rate limited", "1.0"), Nil, Some(OpenapiComponent(Map.empty)), Nil)

    val ref = OpenapiHeaderRef(OpenapiSchemaRef("#/components/headers/Missing"))

    val thrown = intercept[IllegalStateException](ref.resolved("X-Rate-Limit", doc))
    thrown.getMessage should include("is referenced but not found")
  }
}
