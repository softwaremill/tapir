package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.capabilities.Streams
import sttp.tapir.EndpointIO.Example
import sttp.tapir.docs.openapi.dtos.{Author, Book, Country, Genre}
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.tests.data.{Entity, FruitConst, Organization, Person}
import sttp.tapir.{endpoint, _}

import java.nio.ByteBuffer
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

class VerifyYamlExampleTest extends AnyFunSuite with Matchers {

  test("support const attribute") {
    val expectedYaml = load("example/expected_single_example_with_const.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.get.out(jsonBody[FruitConst]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support example of list and not-list types") {
    val expectedYaml = load("example/expected_examples_of_list_and_not_list_types.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post
          .in(query[List[String]]("friends").example(List("bob", "alice")))
          .in(query[String]("current-person").example("alan"))
          .in(jsonBody[Person].example(Person("bob", 23))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support multiple examples with explicit names") {
    val expectedYaml = load("example/expected_multiple_examples_with_names.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post
          .out(
            jsonBody[Entity].examples(
              List(
                Example.of(Person("michal", 40), Some("Michal"), Some("Some summary")).description("Some description"),
                Example.of(Organization("acme"), Some("Acme"))
              )
            )
          ),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support multiple examples with default names") {
    val expectedYaml = load("example/expected_multiple_examples_with_default_names.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post
          .in(jsonBody[Person].example(Person("bob", 23)).example(Person("matt", 30))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support example name even if there is a single example") {
    val expectedYaml = load("example/expected_single_example_with_name.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post
          .out(
            jsonBody[Entity].example(
              Example.of(Person("michal", 40), Some("Michal"), Some("Some summary"))
            )
          ),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support multiple examples with both explicit and default names ") {
    val expectedYaml = load("example/expected_multiple_examples_with_explicit_and_default_names.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post
          .in(jsonBody[Person].examples(List(Example.of(Person("bob", 23), name = Some("Bob")), Example.of(Person("matt", 30))))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support examples in different IO params") {
    val expectedYaml = load("example/expected_multiple_examples.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post
          .in(path[String]("country").example("Poland").example("UK"))
          .in(query[String]("current-person").example("alan").example("bob"))
          .in(jsonBody[Person].example(Person("bob", 23)).example(Person("alan", 50)))
          .in(header[String]("X-Forwarded-User").example("user1").example("user2"))
          .in(cookie[String]("cookie-param").example("cookie1").example("cookie2"))
          .out(jsonBody[Entity].example(Person("michal", 40)).example(Organization("acme"))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("automatically add example for fixed header") {
    val expectedYaml = load("example/expected_fixed_header_example.yml")

    val e = endpoint.in(header("Content-Type", "application/json"))
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using schema with custom example") {
    val expectedYaml = load("example/expected_schema_example.yml")

    val expectedDateTime = ZonedDateTime.of(2021, 1, 1, 1, 1, 1, 1, UTC)
    val expectedBook = Book("title", Genre("name", "desc"), 2021, Author("name", Country("country")))

    implicit val testSchemaZonedDateTime: Schema[ZonedDateTime] = Schema.schemaForZonedDateTime.encodedExample(expectedDateTime)
    implicit val testSchemaBook: Schema[Book] = {
      val schema: Schema[Book] = implicitly[Derived[Schema[Book]]].value
      schema.encodedExample(circeCodec[Book](implicitly, implicitly, schema).encode(expectedBook))
    }

    val endpoint_with_dateTimes = endpoint.post.in(jsonBody[ZonedDateTime]).out(jsonBody[Book])

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint_with_dateTimes, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support schema examples with multiple values") { // https://github.com/softwaremill/sttp-apispec/issues/49
    val expectedYaml = load("example/expected_schema_example_multiple_value.yml")

    implicit val testSchema: Schema[List[Int]] = Schema.schemaForInt.asIterable[List].encodedExample(List(1, 2, 3))
    case class ContainsList(l: List[Int])

    val e = endpoint.post.in(jsonBody[ContainsList])

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support encoded examples for streaming bodies") {
    val expectedYaml = load("example/expected_stream_example.yml")

    trait TestStreams extends Streams[TestStreams] {
      override type BinaryStream = Vector[Byte]
      override type Pipe[X, Y] = Nothing
    }
    object TestStreams extends TestStreams

    val e = endpoint.post.in(
      streamBody(TestStreams)(implicitly[Schema[Person]], CodecFormat.Json())
        .encodedExample(implicitly[Codec[String, Person, CodecFormat.Json]].encode(Person("michal", 40)))
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support summary and name for single example") {
    val e = endpoint.in(
      "users" / query[Option[Boolean]]("active")
        .description("Filter for only active or inactive users.")
        .example(Example.of(value = Some(true), name = Some("For active users"), summary = Some("Get only active users")))
    )

    val expectedYaml = load("example/expected_single_example_with_summary.yml")
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Users", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should support description for single example") {
    val e = endpoint.in(
      "users" / query[Option[Boolean]]("active")
        .description("Filter for only active or inactive users.")
        .example(Example.of(value = Some(true)).description("Get only active users"))
    )

    val expectedYaml = load("example/expected_single_example_with_description.yml")
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Users", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should support byte buffer examples") {
    val e = endpoint.out(
      byteBufferBody
        .example(Example.of(ByteBuffer.wrap("a,b,c,1024,e,f,42,g h,i".getBytes("UTF-8"))))
        .and(header("Content-Type", "text/csv"))
    )

    val expectedYaml = load("example/expected_byte_buffer_example.yml")
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Users", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}
