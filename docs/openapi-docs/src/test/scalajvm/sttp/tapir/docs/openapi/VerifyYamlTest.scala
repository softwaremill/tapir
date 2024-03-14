package sttp.tapir.docs.openapi

import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.circe.yaml.Printer.StringStyle
import io.circe.yaml.Printer.StringStyle.{DoubleQuoted, Literal}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.model.{HeaderNames, Method, StatusCode}
import sttp.apispec.openapi._
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.{default, description, encodedExample}
import sttp.tapir.SchemaType.SProductField
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.openapi.VerifyYamlTest._
import sttp.tapir.docs.openapi.dtos.Book
import sttp.tapir.docs.openapi.dtos.VerifyYamlTestData._
import sttp.tapir.docs.openapi.dtos.VerifyYamlTestData2._
import sttp.tapir.docs.openapi.dtos.a.{Pet => APet}
import sttp.tapir.docs.openapi.dtos.b.{Pet => BPet}
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.tests.Basic._
import sttp.tapir.tests.Multipart
import sttp.tapir.tests.data.{FruitAmount, Person}
import sttp.tapir.{Endpoint, endpoint, header, path, query, stringBody, ModifyEach => _, _}

import java.time.{Instant, LocalDateTime}

class VerifyYamlTest extends AnyFunSuite with Matchers {
  val all_the_way: Endpoint[Unit, (FruitAmount, String), Unit, (FruitAmount, Int), Any] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo[FruitAmount])
    .in(query[String]("color"))
    .out(jsonBody[FruitAmount])
    .out(header[Int]("X-Role"))

  test("should match the expected yaml") {
    val expectedYaml = load("expected.yml")

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(List(in_query_query_out_string, all_the_way, delete_endpoint), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using OpenAPI 3.0") {
    val expectedYaml = load("expected.yml")

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(List(in_query_query_out_string, all_the_way, delete_endpoint), Info("Fruits", "1.0")).toYaml3_0_3
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when there are external references") {
    val external_reference: PublicEndpoint[Unit, Problem, Unit, Any] =
      endpoint.errorOut(jsonBody[Problem])

    val expectedYaml = load("expected_external.yml")

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(List(external_reference), Info("Fruits", "1.0")).toYaml(StringStyle.SingleQuoted)

    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  val endpoint_wit_recursive_structure: Endpoint[Unit, Unit, Unit, F1, Any] = endpoint.out(jsonBody[F1])

  test("should match the expected yaml when schema is recursive") {
    val expectedYaml = load("expected_recursive.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint_wit_recursive_structure, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support providing custom schema name") {
    def customSchemaName(name: SName) = (name.fullName +: name.typeParameterShortNames).mkString("_")
    val options = OpenAPIDocsOptions.default.copy(OpenAPIDocsOptions.defaultOperationIdGenerator, customSchemaName)
    val expectedYaml = load("expected_custom_schema_name.yml")

    val actualYaml =
      OpenAPIDocsInterpreter(options).toOpenAPI(List(in_query_query_out_string, all_the_way, delete_endpoint), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use custom operationId generator") {
    def customOperationIdGenerator(e: AnyEndpoint, pc: Vector[String], m: Method) =
      pc.map(_.toUpperCase).mkString("", "+", "-") + m.method.toUpperCase
    val options = OpenAPIDocsOptions.default.copy(customOperationIdGenerator)
    val expectedYaml = load("expected_custom_operation_id.yml")

    val actualYaml =
      OpenAPIDocsInterpreter(options).toOpenAPI(in_query_query_out_string.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = Vector[Byte]
    override type Pipe[X, Y] = Nothing
  }
  object TestStreams extends TestStreams

  val streaming_endpoint: Endpoint[Unit, Vector[Byte], Unit, Vector[Byte], TestStreams] = endpoint
    .in(streamTextBody(TestStreams)(CodecFormat.TextPlain()))
    .out(streamBinaryBody(TestStreams)(CodecFormat.OctetStream()))

  test("should match the expected yaml for streaming endpoints") {
    val expectedYaml = load("expected_streaming.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(streaming_endpoint, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support tags") {
    val userTaggedEndpointShow = endpoint.tag("user").in("user" / "show").get.out(stringBody)
    val userTaggedEdnpointSearch = endpoint.tags(List("user", "admin")).in("user" / "search").get.out(stringBody)
    val adminTaggedEndpointAdd = endpoint.tag("admin").in("admin" / "add").get.out(stringBody)

    val expectedYaml = load("expected_tags.yml")

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(List(userTaggedEndpointShow, userTaggedEdnpointSearch, adminTaggedEndpointAdd), Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml for general info") {
    val expectedYaml = load("expected_general_info.yml")

    val api = Info(
      "Fruits",
      "1.0",
      description = Some("Fruits are awesome"),
      termsOfService = Some("our.terms.of.service"),
      contact = Some(Contact(Some("Author"), Some("tapir@softwaremill.com"), Some("tapir.io"))),
      license = Some(License("MIT", Some("mit.license")))
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(in_query_query_out_string, api).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multipart") {
    val expectedYaml = load("expected_multipart.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Multipart.in_file_multipart_out_multipart, "Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support empty bodies") {
    val expectedYaml = load("expected_empty.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should keep the order of multiple endpoints") {
    val expectedYaml = load("expected_multiple.yml")

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        List(endpoint.in("p1"), endpoint.in("p3"), endpoint.in("p2"), endpoint.in("p5"), endpoint.in("p4")),
        Info("Fruits", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should handle classes with same name") {
    val e: Endpoint[Unit, APet, Unit, BPet, Any] = endpoint
      .in(jsonBody[APet])
      .out(jsonBody[BPet])
    val expectedYaml = load("expected_same_fullnames.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold nested hierarchy") {
    val e: Endpoint[Unit, Book, Unit, String, Any] = endpoint
      .in(jsonBody[Book])
      .out(stringBody)
    val expectedYaml = load("expected_unfolded_hierarchy.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays") {
    val e = endpoint.in(jsonBody[List[FruitAmount]]).out(stringBody)
    val expectedYaml = load("expected_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  // #1168
  test("should unfold options") {
    val e = endpoint.post.in(jsonBody[ObjectWithOption])

    val expectedYaml = load("expected_unfolded_option.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold options with descriptions") {
    implicit val objectWithOptionSchema: Schema[ObjectWithOption] = {
      val base = implicitly[Derived[Schema[ObjectWithOption]]].value
      base.modify(_.data)(_.description("Amount of fruits"))
    }
    val e = endpoint.post.in(jsonBody[ObjectWithOption])

    val expectedYaml = load("expected_unfolded_option_description.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic type is used multiple times") {
    val expectedYaml = load("expected_generic.yml")

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(List(endpoint.in("p1" and jsonBody[G[String]]), endpoint.in("p2" and jsonBody[G[Int]])), Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold objects from unfolded arrays") {
    val expectedYaml = load("expected_unfolded_object_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use descriptions from custom schemas of nested objects") {
    val expectedYaml = load("expected_descriptions_in_nested_custom_schemas.yml")

    import SchemaType._
    implicit val customFruitAmountSchema: Schema[FruitAmount] = Schema(
      SProduct(
        List(
          SProductField(FieldName("fruit"), Schema(SString()), (_: FruitAmount) => None),
          SProductField(FieldName("amount"), Schema(SInteger()).format("int32"), (_: FruitAmount) => None)
        )
      ),
      Some(SName("tapir.tests.data.FruitAmount", Nil))
    ).description("Amount of fruits")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.post.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use descriptions from customised derived schemas") {
    val expectedYaml = load("expected_descriptions_in_nested_custom_schemas.yml")

    implicit val customFruitAmountSchema: Schema[FruitAmount] = implicitly[Derived[Schema[FruitAmount]]].value
      .description("Amount of fruits")
      .modifyUnsafe[Nothing]("amount")(_.format("int32"))

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.post.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays from object") {
    val expectedYaml = load("expected_unfolded_array_unfolded_object.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(jsonBody[ObjectWithList]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should add uniqueItems for set-based array schema") {
    val expectedYaml = load("expected_unfolded_array_with_unique_items.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(jsonBody[ObjectWithSet]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed status code output in response") {
    val expectedYaml = load("expected_fixed_status_code.yml")

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(endpoint.out(statusCode(StatusCode.PermanentRedirect)).out(header[String]("Location")), Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render additional properties for map") {
    val expectedYaml = load("expected_additional_properties.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(jsonBody[Map[String, Person]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render fields with additional properties for map for manually provided schema") {
    val expectedYaml = load("expected_fields_with_additional_properties.yml")
    case class FailureInput(status: Int, message: String, additionalMap: Map[String, String])

    implicit val schemaProviderForFailureInput: Schema[FailureInput] = Schema(
      SchemaType.SOpenProduct(
        List(
          SProductField[FailureInput, Int](FieldName("status"), Schema.schemaForInt, x => Some(x.status)),
          SProductField[FailureInput, String](FieldName("message"), Schema.schemaForString, x => Some(x.message))
        ),
        Schema.string
      )(_.additionalMap),
      Some(Schema.SName("FailureInput"))
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(jsonBody[FailureInput]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render map with plain values") {
    val expectedYaml = load("expected_map_with_plain_values.yml")

    val openApi = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(jsonBody[Map[String, String]]), Info("Entities", "1.0"))

    val actualYaml = openApi.toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  // #118
  test("use fixed status code output in response if it's the only output") {
    val expectedYaml = load("expected_fixed_status_code_2.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(statusCode(StatusCode.NoContent)), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support prepending inputs") {
    val expectedYaml = load("expected_prepended_input.yml")

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(in_query_query_out_string.in("add").prependIn("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("use fixed header output in response") {
    val expectedYaml = load("expected_fixed_header_output_response.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(header("Location", "Poland")), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed header input in request") {
    val expectedYaml = load("expected_fixed_header_input_request.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.in(header("Location", "Poland")), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("arbitrary json output") {
    val expectedYaml = load("expected_arbitrary_json_output.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint
          .out(jsonBody[Json]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("deprecated endpoint") {
    val expectedYaml = load("expected_deprecated.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.in("api").deprecated(),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should not set format for array types ") {
    val expectedYaml = load("expected_array_no_format.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint
          .in(query[List[String]]("foo"))
          .in(query[List[Long]]("bar")),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml for single server with variables") {
    val expectedYaml = load("expected_single_server_with_variables.yml")

    val api = Info(
      "Fruits",
      "1.0"
    )
    val servers = List(
      Server("https://{username}.example.com:{port}/{basePath}")
        .description("The production API server")
        .variables(
          "username" -> ServerVariable(None, "demo", Some("Username")),
          "port" -> ServerVariable(Some(List("8443", "443")), "8443", None),
          "basePath" -> ServerVariable(None, "v2", None)
        )
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(in_query_query_out_string, api).servers(servers).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml for multiple servers") {
    val expectedYaml = load("expected_multiple_servers.yml")

    val api = Info(
      "Fruits",
      "1.0"
    )
    val servers = List(
      Server("https://development.example.com/v1", Some("Development server"), None),
      Server("https://staging.example.com/v1", Some("Staging server"), None),
      Server("https://api.example.com/v1", Some("Production server"), None)
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(in_query_query_out_string, api).servers(servers).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use date-time format for Instant fields") {
    val expectedYaml = load("expected_date_time.yml")

    val e = endpoint.in(query[Instant]("instant"))
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use string format for LocalDateTime fields") {

    val expectedYaml = load("expected_localDateTime.yml")

    val e = endpoint.in(query[LocalDateTime]("localDateTime"))

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("exclusive bounds") {
    val expectedYaml = load("expected_exclusive_bounds.yml")

    val qParam = query[Int]("num")
      .validate(Validator.min(0, exclusive = true))
      .validate(Validator.max(42, exclusive = true))
    val e = endpoint.in(qParam)
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use default for a query parameter") {
    val expectedYaml = load("expected_default_query_param.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post.in(query[String]("name").example("alan").default("tom")),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  // #1800
  test("should use default for a request body") {
    val expectedYaml = load("expected_default_request_body.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post.in(jsonBody[ObjectWithDefaults]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml using double quoted style") {
    val ep = endpoint.get.description("first line:\nsecond line")

    val expectedYaml = load("expected_double_quoted.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(ep, "String style", "1.0").toYaml(DoubleQuoted)
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml using literal style") {
    val ep = endpoint.get.description("first line:\nsecond line")

    val expectedYaml = load("expected_literal.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(ep, "String style", "1.0").toYaml(Literal)
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should apply openapi extensions in correct places") {
    import sttp.tapir.docs.apispec.DocsExtensionAttribute._

    case class MyExtension(string: String, int: Int)

    val sampleEndpoint =
      endpoint.post
        .in("path-hello" / path[String]("world").docsExtension("x-path", 22))
        .in(query[String]("hi").docsExtension("x-query", 33))
        .in(jsonBody[FruitAmount].docsExtension("x-request", MyExtension("a", 1)))
        .out(jsonBody[FruitAmount].docsExtension("x-response", List("array-0", "array-1")).docsExtension("x-response", "foo"))
        .errorOut(stringBody.docsExtension("x-error", "error-extension"))
        .docsExtension("x-endpoint-level-string", "world")
        .docsExtension("x-endpoint-level-int", 11)
        .docsExtension("x-endpoint-obj", MyExtension("42.42", 42))

    val rootExtensions = List(
      DocsExtension.of("x-root-bool", true),
      DocsExtension.of("x-root-list", List(1, 2, 4))
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(sampleEndpoint, Info("title", "1.0"), rootExtensions).toYaml

    noIndentation(actualYaml) shouldBe load("expected_extensions.yml")
  }

  test("should add openapi extensions to the schema") {
    import sttp.tapir.docs.apispec.DocsExtensionAttribute._

    case class MyExtension(string: String, int: Int)
    val sampleEndpoint = endpoint.post.in(jsonBody[FruitAmount].schema(_.docsExtension("x-schema", MyExtension("a", 1))))

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(sampleEndpoint, Info("title", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe load("expected_extensions_schema.yml")
  }

  test("should add openapi extensions to the security scheme") {
    import sttp.tapir.docs.apispec.DocsExtensionAttribute._

    case class MyExtension(string: String, int: Int)
    val sampleEndpoint =
      endpoint.post.in(auth.apiKey(header[String](HeaderNames.Authorization)).docsExtension("x-schema", MyExtension("a", 1)))

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(sampleEndpoint, Info("title", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe load("expected_extensions_security_scheme.yml")
  }

  test("should include a response even if all outputs are empty, with descriptions") {
    sealed trait Base
    case object Success extends Base
    case object AnotherSuccess extends Base

    val ep = infallibleEndpoint.get.out(
      sttp.tapir.oneOf[Base](
        oneOfVariant(StatusCode.Ok, emptyOutputAs(Success).description("success")),
        oneOfVariant(StatusCode.Accepted, emptyOutputAs(AnotherSuccess).description("maybe success"))
      )
    )

    val expectedYaml = load("expected_multi_empty_outputs.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(ep, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("explicit Content-Type header should have priority over the codec") {
    val ep = out_fixed_content_type_header

    val expectedYaml = load("expected_explicit_content_type_header.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(ep, "title", "1.0").toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should contain description field for Option[Json] field") {
    val expectedYaml = load("expected_type_and_description_for_circe_json.yml")

    case class ExampleMessageIn(
        @description("Circe Json Option description")
        maybeJson: Option[Json] = Some(Json.fromString("test"))
    )

    val myEndpoint = endpoint.post
      .in(jsonBody[ExampleMessageIn])

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(myEndpoint, Info("Circe Jason Option", "1.0"))
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should mark optional fields as nullable when configured to do so") {
    case class ClassWithOptionField(optionalIntField: Option[Int], requiredStringField: String)

    val e = endpoint.in(jsonBody[ClassWithOptionField]).out(stringBody)
    val expectedYaml = load("expected_nullable_option_field.yml")

    val options = OpenAPIDocsOptions.default.copy(markOptionsAsNullable = true)

    val actualYaml = OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("ClassWithOptionField", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should mark optional fields as nullable when configured to do so using OpenAPI 3.0") {
    case class ClassWithOptionField(optionalIntField: Option[Int], requiredStringField: String)

    val e = endpoint.in(jsonBody[ClassWithOptionField]).out(stringBody)
    val expectedYaml = load("expected_nullable_option_field_303.yml")

    val options = OpenAPIDocsOptions.default.copy(markOptionsAsNullable = true)

    val actualYaml = OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("ClassWithOptionField", "1.0")).copy(openapi = "3.0.3").toYaml3_0_3
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should mark optional class fields as nullable when configured to do so") {
    case class Bar(bar: Int)
    case class ClassWithOptionClassField(optionalObjField: Option[Bar], requiredStringField: String)

    val e = endpoint.in(jsonBody[ClassWithOptionClassField]).out(stringBody).post
    val expectedYaml = load("expected_nullable_option_class_field.yml")

    val options = OpenAPIDocsOptions.default.copy(markOptionsAsNullable = true)

    val actualYaml = OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("ClassWithOptionClassField", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should mark optional class fields as nullable when configured to do so using OpenAPI 3.0") {
    case class Bar(bar: Int)
    case class ClassWithOptionClassField(optionalObjField: Option[Bar], requiredStringField: String)

    val e = endpoint.in(jsonBody[ClassWithOptionClassField]).out(stringBody).post
    val expectedYaml = load("expected_nullable_option_class_field_303.yml")

    val options = OpenAPIDocsOptions.default.copy(markOptionsAsNullable = true)

    val actualYaml =
      OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("ClassWithOptionClassField", "1.0")).copy(openapi = "3.0.3").toYaml3_0_3
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should generate full schema name for type params") {
    val e = endpoint.out(jsonBody[Map[String, FruitAmount]])
    val expectedYaml = load("expected_full_schema_names.yml")

    val options = OpenAPIDocsOptions.default.copy(schemaName = info => {
      (info.fullName +: info.typeParameterShortNames).flatMap(_.split('.')).mkString("_")
    })

    val actualYaml = OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should generate default and example values for nested optional fields") {
    case class Nested(nestedValue: String)
    case class ClassWithNestedOptionalField(
        @encodedExample(Nested("foo").asJson) @default(Some(Nested("foo")), Some("""{"nestedValue": "foo"}""")) value: Option[Nested]
    )

    val e = endpoint.in(jsonBody[ClassWithNestedOptionalField])
    val expectedYaml = load("expected_default_and_example_on_nested_option_field.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("ClassWithNestedOptionalField", "1.0")).toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should respect hidden annotation") {
    val hide_in_docs: Endpoint[(String, String), (Int, String, Int, String, String, String), Unit, List[String], Any] =
      endpoint.get
        .securityIn("auth" / "hidden".schema(_.copy(hidden = true)))
        .securityIn(header[String]("s1"))
        .securityIn(header[String]("s2").schema(_.copy(hidden = true)))
        .in("api" / "echo" / "headers".schema(_.copy(hidden = true)))
        .in(cookie[Int]("c1"))
        .in(cookie[String]("c2").schema(_.copy(hidden = true)))
        .in(query[Int]("q1"))
        .in(query[String]("q2").schema(_.copy(hidden = true)))
        .in(header[String]("h1"))
        .in(header[String]("h2").schema(_.copy(hidden = true)))
        .out(header[List[String]]("Set-Cookie"))

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(hide_in_docs, Info("Hide in docs", "1.0"))
      .toYaml

    val expectedYaml = load("hide_in_docs.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should not duplicate open api path parameters") {
    val correlationHeader = header[String]("correlation-id")

    val ep = endpoint.securityIn(correlationHeader).in(correlationHeader)

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(ep, Info("Unique parameters", "1.0"))
      .toYaml

    val expectedYaml = load("expected_unique_open_api_parameters.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should contain named schema component and values for enumeration") {
    implicit val numberCodec: io.circe.Codec[Number] = null

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(endpoint.in("numbers").in(jsonBody[Number]), Info("Numbers", "1.0"))
      .toYaml

    val expectedYaml = load("expected_enumeration_values.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should add allowEmptyValues for flag query parameters") {
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(in_flag_query_out_string, Info("Flags", "1.0"))
      .toYaml

    val expectedYaml = load("expected_flag_query.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should add operation callback") {
    case class TriggerRequest(callbackUrl: String)
    case class CallbackRequest(answer: String)

    val (callback, reusableCallback, callbackRequest) = {
      val throwaway = OpenAPIDocsInterpreter()
        .toOpenAPI(
          List(
            endpoint.put.in("callback").in(jsonBody[CallbackRequest]),
            endpoint.delete.in("reusable_callback").in(jsonBody[CallbackRequest])
          ),
          Info("Throwaway", "1.0")
        )

      val callbackItem = throwaway.paths.pathItems("/callback")
      val reusableCallbackItem = throwaway.paths.pathItems("/reusable_callback")
      val callback = Callback().addPathItem("{$request.body#/callbackUrl}", callbackItem)
      val reusableCallback = Callback().addPathItem("{$request.body#/callbackUrl}", reusableCallbackItem)
      (callback, reusableCallback, throwaway.components.get.schemas("CallbackRequest"))
    }

    val docs: OpenAPI = OpenAPIDocsInterpreter()
      .toOpenAPI(endpoint.put.in("trigger").in(jsonBody[TriggerRequest]), Info("Callbacks", "1.0"))

    val reusableCallbackKey = "reusable_callback"

    import com.softwaremill.quicklens._
    val actualYaml = docs
      .modify(_.paths.pathItems.at("/trigger").put.each)
      .using(_.addCallback("my_callback", callback).addCallbackReference("my_reusable_callback", reusableCallbackKey))
      .modify(_.components.each)
      .using(_.addCallback(reusableCallbackKey, reusableCallback))
      .modify(_.components.each.schemas)
      .using(_ + ("CallbackRequest" -> callbackRequest))
      .toYaml

    val expectedYaml = load("expected_callbacks.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should add application/json content for json query parameter") {
    val expectedYaml = load("expected_json_query_param.yml")
    val codec = Codec.listHead(Codec.json[String](DecodeResult.Value(_))(identity))
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post.in(queryAnyFormat[String, CodecFormat.Json]("name", codec).example("alan").default("tom")),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }
}

object VerifyYamlTest {
  case class Problem()

  object Problem {
    implicit val schema: Schema[Problem] =
      Schema[Problem](
        SchemaType.SRef(
          Schema.SName("https://example.com/models/model.yaml#/Problem")
        )
      )
  }

  object Numbers extends Enumeration {
    val One, Two, Three = Value
  }

  case class Number(value: Numbers.Value)
}
