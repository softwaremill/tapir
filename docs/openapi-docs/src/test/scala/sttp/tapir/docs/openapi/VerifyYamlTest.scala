package sttp.tapir.docs.openapi

import io.circe.Json
import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import io.circe.yaml.Printer.StringStyle.{DoubleQuoted, Literal}
import sttp.model.{Method, StatusCode}
import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.docs.openapi.VerifyYamlTest._
import sttp.tapir.docs.openapi.dtos.Book
import sttp.tapir.docs.openapi.dtos.a.{Pet => APet}
import sttp.tapir.docs.openapi.dtos.b.{Pet => BPet}
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.openapi._
import sttp.tapir.tests.{Person, _}
import sttp.tapir.{Endpoint, endpoint, _}

import java.time.{Instant, LocalDateTime}

class VerifyYamlTest extends AnyFunSuite with Matchers {
  val all_the_way: Endpoint[(FruitAmount, String), Unit, (FruitAmount, Int), Any] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(jsonBody[FruitAmount])
    .out(header[Int]("X-Role"))

  test("should match the expected yaml") {
    val expectedYaml = load("expected.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(List(in_query_query_out_string, all_the_way, delete_endpoint), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  val endpoint_wit_recursive_structure: Endpoint[Unit, Unit, F1, Any] = endpoint
    .out(jsonBody[F1])

  test("should match the expected yaml when schema is recursive") {
    val expectedYaml = load("expected_recursive.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_recursive_structure, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support providing custom schema name") {
    def customSchemaName(info: SObjectInfo) = (info.fullName +: info.typeParameterShortNames).mkString("_")
    val options = OpenAPIDocsOptions.default.copy(OpenAPIDocsOptions.defaultOperationIdGenerator, customSchemaName)
    val expectedYaml = load("expected_custom_schema_name.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(List(in_query_query_out_string, all_the_way, delete_endpoint), Info("Fruits", "1.0"))(options).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use custom operationId generator") {
    def customOperationIdGenerator(pc: Vector[String], m: Method) = pc.map(_.toUpperCase).mkString("", "+", "-") + m.method.toUpperCase
    val options = OpenAPIDocsOptions.default.copy(customOperationIdGenerator)
    val expectedYaml = load("expected_custom_operation_id.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(in_query_query_out_string.in("add").in("path"), Info("Fruits", "1.0"))(options).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = Vector[Byte]
    override type Pipe[X, Y] = Nothing
  }
  object TestStreams extends TestStreams

  val streaming_endpoint: Endpoint[Vector[Byte], Unit, Vector[Byte], TestStreams] = endpoint
    .in(streamTextBody(TestStreams)(CodecFormat.TextPlain()))
    .out(streamBinaryBody(TestStreams))

  test("should match the expected yaml for streaming endpoints") {
    val expectedYaml = load("expected_streaming.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(streaming_endpoint, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support tags") {
    val userTaggedEndpointShow = endpoint.tag("user").in("user" / "show").get.out(stringBody)
    val userTaggedEdnpointSearch = endpoint.tags(List("user", "admin")).in("user" / "search").get.out(stringBody)
    val adminTaggedEndpointAdd = endpoint.tag("admin").in("admin" / "add").get.out(stringBody)

    val expectedYaml = load("expected_tags.yml")

    val actualYaml = OpenAPIDocsInterpreter
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

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(in_query_query_out_string, api).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multipart") {
    val expectedYaml = load("expected_multipart.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(in_file_multipart_out_multipart, "Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support empty bodies") {
    val expectedYaml = load("expected_empty.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should keep the order of multiple endpoints") {
    val expectedYaml = load("expected_multiple.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        List(endpoint.in("p1"), endpoint.in("p3"), endpoint.in("p2"), endpoint.in("p5"), endpoint.in("p4")),
        Info("Fruits", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should handle classes with same name") {
    val e: Endpoint[APet, Unit, BPet, Any] = endpoint
      .in(jsonBody[APet])
      .out(jsonBody[BPet])
    val expectedYaml = load("expected_same_fullnames.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold nested hierarchy") {
    val e: Endpoint[Book, Unit, String, Any] = endpoint
      .in(jsonBody[Book])
      .out(stringBody)
    val expectedYaml = load("expected_unfolded_hierarchy.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays") {
    val e = endpoint.in(jsonBody[List[FruitAmount]]).out(stringBody)
    val expectedYaml = load("expected_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic type is used multiple times") {
    val expectedYaml = load("expected_generic.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(List(endpoint.in("p1" and jsonBody[G[String]]), endpoint.in("p2" and jsonBody[G[Int]])), Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold objects from unfolded arrays") {
    val expectedYaml = load("expected_unfolded_object_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use descriptions from custom schemas of nested objects") {
    val expectedYaml = load("expected_descriptions_in_nested_custom_schemas.yml")

    import SchemaType._
    implicit val customFruitAmountSchema: Schema[FruitAmount] = Schema(
      SProduct(
        SObjectInfo("tapir.tests.FruitAmount", Nil),
        List(
          SProductField(FieldName("fruit"), Schema(SString()), (_: FruitAmount) => None),
          SProductField(FieldName("amount"), Schema(SInteger()).format("int32"), (_: FruitAmount) => None)
        )
      )
    ).description("Amount of fruits")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.post.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use descriptions from customised derived schemas") {
    val expectedYaml = load("expected_descriptions_in_nested_custom_schemas.yml")

    implicit val customFruitAmountSchema: Schema[FruitAmount] = implicitly[Derived[Schema[FruitAmount]]].value
      .description("Amount of fruits")
      .modifyUnsafe[Nothing]("amount")(_.format("int32"))

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.post.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays from object") {
    val expectedYaml = load("expected_unfolded_array_unfolded_object.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[ObjectWithList]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed status code output in response") {
    val expectedYaml = load("expected_fixed_status_code.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(endpoint.out(statusCode(StatusCode.PermanentRedirect)).out(header[String]("Location")), Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render additional properties for map") {
    val expectedYaml = load("expected_additional_properties.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[Map[String, Person]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render map with plain values") {
    val expectedYaml = load("expected_map_with_plain_values.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[Map[String, String]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  // #118
  test("use fixed status code output in response if it's the only output") {
    val expectedYaml = load("expected_fixed_status_code_2.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(statusCode(StatusCode.NoContent)), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support prepending inputs") {
    val expectedYaml = load("expected_prepended_input.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(in_query_query_out_string.in("add").prependIn("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("use fixed header output in response") {
    val expectedYaml = load("expected_fixed_header_output_response.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(header("Location", "Poland")), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed header input in request") {
    val expectedYaml = load("expected_fixed_header_input_request.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.in(header("Location", "Poland")), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("arbitrary json output") {
    val expectedYaml = load("expected_arbitrary_json_output.yml")
    val actualYaml = OpenAPIDocsInterpreter
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
    val actualYaml = OpenAPIDocsInterpreter
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
    val actualYaml = OpenAPIDocsInterpreter
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

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(in_query_query_out_string, api).servers(servers).toYaml
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

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(in_query_query_out_string, api).servers(servers).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use date-time format for Instant fields") {
    val expectedYaml = load("expected_date_time.yml")

    val e = endpoint.in(query[Instant]("instant"))
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use string format for LocalDateTime fields") {

    val expectedYaml = load("expected_localDateTime.yml")

    val e = endpoint.in(query[LocalDateTime]("localDateTime"))

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("exclusive bounds") {
    val expectedYaml = load("expected_exclusive_bounds.yml")

    val qParam = query[Int]("num")
      .validate(Validator.min(0, exclusive = true))
      .validate(Validator.max(42, exclusive = true))
    val e = endpoint.in(qParam)
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use default for a query parameter") {
    val expectedYaml = load("expected_default_query_param.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post.in(query[String]("name").example("alan").default("tom")),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml using double quoted style") {
    val ep = endpoint.get.description("first line:\nsecond line")

    val expectedYaml = load("expected_double_quoted.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(ep, "String style", "1.0").toYaml(DoubleQuoted)
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml using literal style") {
    val ep = endpoint.get.description("first line:\nsecond line")

    val expectedYaml = load("expected_literal.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(ep, "String style", "1.0").toYaml(Literal)
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }
}

object VerifyYamlTest {
  case class F1(data: List[F1])
  case class G[T](data: T)
  case class ObjectWrapper(value: FruitAmount)
  case class ObjectWithList(data: List[FruitAmount])
}
