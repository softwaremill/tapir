package sttp.tapir.docs.openapi

import java.time.Instant
import io.circe.Json
import io.circe.generic.auto._
import sttp.model.{Method, StatusCode}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.docs.openapi.dtos.Book
import sttp.tapir.docs.openapi.dtos.a.{Pet => APet}
import sttp.tapir.docs.openapi.dtos.b.{Pet => BPet}
import sttp.tapir.generic.{Configuration, Derived}
import sttp.tapir.json.circe._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.openapi.{Contact, Info, License, Server, ServerVariable}
import sttp.tapir.tests._

import scala.collection.immutable.ListMap
import scala.io.Source
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.model.UsernamePassword

class VerifyYamlTest extends AnyFunSuite with Matchers {
  val all_the_way: Endpoint[(FruitAmount, String), Unit, (FruitAmount, Int), Any] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(jsonBody[FruitAmount])
    .out(header[Int]("X-Role"))

  test("should match the expected yaml") {
    val expectedYaml = loadYaml("expected.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(List(in_query_query_out_string, all_the_way, delete_endpoint), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  val endpoint_wit_recursive_structure: Endpoint[Unit, Unit, F1, Any] = endpoint
    .out(jsonBody[F1])

  test("should match the expected yaml when schema is recursive") {
    val expectedYaml = loadYaml("expected_recursive.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_recursive_structure, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support providing custom schema name") {
    def customSchemaName(info: SObjectInfo) = (info.fullName +: info.typeParameterShortNames).mkString("_")
    val options = OpenAPIDocsOptions.default.copy(OpenAPIDocsOptions.defaultOperationIdGenerator, customSchemaName)
    val expectedYaml = loadYaml("expected_custom_schema_name.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(List(in_query_query_out_string, all_the_way, delete_endpoint), Info("Fruits", "1.0"))(options).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use custom operationId generator") {
    def customOperationIdGenerator(pc: Vector[String], m: Method) = pc.map(_.toUpperCase).mkString("", "+", "-") + m.method.toUpperCase
    val options = OpenAPIDocsOptions.default.copy(customOperationIdGenerator)
    val expectedYaml = loadYaml("expected_custom_operation_id.yml")

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
    .in(streamBody(TestStreams)(Schema.string, CodecFormat.TextPlain()))
    .out(streamBody(TestStreams)(Schema.binary, CodecFormat.OctetStream()))

  test("should match the expected yaml for streaming endpoints") {
    val expectedYaml = loadYaml("expected_streaming.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(streaming_endpoint, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support tags") {
    val userTaggedEndpointShow = endpoint.tag("user").in("user" / "show").get.out(stringBody)
    val userTaggedEdnpointSearch = endpoint.tags(List("user", "admin")).in("user" / "search").get.out(stringBody)
    val adminTaggedEndpointAdd = endpoint.tag("admin").in("admin" / "add").get.out(stringBody)

    val expectedYaml = loadYaml("expected_tags.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(List(userTaggedEndpointShow, userTaggedEdnpointSearch, adminTaggedEndpointAdd), Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml for general info") {
    val expectedYaml = loadYaml("expected_general_info.yml")

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
    val expectedYaml = loadYaml("expected_multipart.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(in_file_multipart_out_multipart, "Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support authentication") {
    val expectedYaml = loadYaml("expected_auth.yml")

    val e1 = endpoint.in(auth.bearer[String]()).in("api1" / path[String]).out(stringBody)
    val e2 = endpoint.in(auth.bearer[String]()).in("api2" / path[String]).out(stringBody)
    val e3 = endpoint.in(auth.apiKey(header[String]("apikey"))).in("api3" / path[String]).out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support optional authentication") {
    val expectedYaml = loadYaml("expected_optional_auth.yml")

    val e1 = endpoint.in(auth.bearer[String]()).in("api1" / path[String]).out(stringBody)
    val e2 = endpoint.in(auth.bearer[Option[String]]()).in("api2" / path[String]).out(stringBody)
    val e3 = endpoint.in(auth.apiKey(header[Option[String]]("apikey"))).in("api3" / path[String]).out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support naming of security schemes") {

    val expectedYaml = loadYaml("expected_auth_with_named_schemes.yml")

    val e1 = endpoint.in(auth.bearer[String]().securitySchemeName("secBearer")).in("secure" / "bearer").out(stringBody)
    val e2 = endpoint.in(auth.basic[UsernamePassword]().securitySchemeName("secBasic")).in("secure" / "basic").out(stringBody)
    val e3 =
      endpoint.in(auth.apiKey(header[String]("apikey")).securitySchemeName("secApiKeyHeader")).in("secure" / "apiKeyHeader").out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support Oauth2") {
    val expectedYaml = loadYaml("expected_oauth2.yml")
    val oauth2 =
      auth.oauth2
        .authorizationCode(
          "https://example.com/auth",
          ListMap("client" -> "scope for clients", "admin" -> "administration scope")
        )

    val e1 =
      endpoint
        .in(oauth2)
        .in("api1" / path[String])
        .out(stringBody)
    val e2 =
      endpoint
        .in(oauth2.requiredScopes(Seq("client")))
        .in("api2" / path[String])
        .out(stringBody)
    val e3 =
      endpoint
        .in(oauth2.requiredScopes(Seq("admin")))
        .in("api3" / path[String])
        .out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support empty bodies") {
    val expectedYaml = loadYaml("expected_empty.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple status codes") {
    // given
    val expectedYaml = loadYaml("expected_status_codes.yml")

    // work-around for #10: unsupported sealed trait families
    implicit val schemaForErrorInfo: Schema[ErrorInfo] = Schema[ErrorInfo](SchemaType.SProduct(SchemaType.SObjectInfo("ErrorInfo"), Nil))

    val e = endpoint.errorOut(
      sttp.tapir.oneOf(
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
        statusMapping(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
        statusDefaultMapping(jsonBody[Unknown].description("unknown"))
      )
    )

    // when
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    // then
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple the same status codes") {
    val expectedYaml = loadYaml("expected_the_same_status_codes.yml")

    implicit val unauthorizedTextPlainCodec: Codec[String, Unauthorized, CodecFormat.TextPlain] =
      Codec.string.map(Unauthorized.apply _)(_.realm)

    val e = endpoint.out(
      sttp.tapir.oneOf(
        statusMapping(StatusCode.Ok, jsonBody[NotFound].description("not found")),
        statusMapping(StatusCode.Ok, plainBody[Unauthorized]),
        statusMapping(StatusCode.NoContent, jsonBody[Unknown].description("unknown"))
      )
    )

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should keep the order of multiple endpoints") {
    val expectedYaml = loadYaml("expected_multiple.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        List(endpoint.in("p1"), endpoint.in("p3"), endpoint.in("p2"), endpoint.in("p5"), endpoint.in("p4")),
        Info("Fruits", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types") {
    val expectedYaml = loadYaml("expected_coproduct.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Entity, Any] = endpoint
      .out(jsonBody[Entity])

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types with discriminator") {
    val sPerson = implicitly[Schema[Person]]
    val sOrganization = implicitly[Schema[Organization]]
    implicit val sEntity: Schema[Entity] =
      Schema.oneOfUsingField[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = loadYaml("expected_coproduct_discriminator.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Entity, Any] = endpoint
      .out(jsonBody[Entity])
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types") {
    val expectedYaml = loadYaml("expected_coproduct_nested.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, NestedEntity, Any] = endpoint
      .out(jsonBody[NestedEntity])

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types with discriminator") {
    val sPerson = implicitly[Schema[Person]]
    val sOrganization = implicitly[Schema[Organization]]
    implicit val sEntity: Schema[Entity] =
      Schema.oneOfUsingField[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = loadYaml("expected_coproduct_discriminator_nested.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, NestedEntity, Any] = endpoint
      .out(jsonBody[NestedEntity])
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should handle classes with same name") {
    val e: Endpoint[APet, Unit, BPet, Any] = endpoint
      .in(jsonBody[APet])
      .out(jsonBody[BPet])
    val expectedYaml = loadYaml("expected_same_fullnames.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold nested hierarchy") {
    val e: Endpoint[Book, Unit, String, Any] = endpoint
      .in(jsonBody[Book])
      .out(stringBody)
    val expectedYaml = loadYaml("expected_unfolded_hierarchy.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays") {
    val e = endpoint.in(jsonBody[List[FruitAmount]]).out(stringBody)
    val expectedYaml = loadYaml("expected_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic type is used multiple times") {
    val expectedYaml = loadYaml("expected_generic.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(List(endpoint.in("p1" and jsonBody[G[String]]), endpoint.in("p2" and jsonBody[G[Int]])), Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold objects from unfolded arrays") {
    val expectedYaml = loadYaml("expected_unfolded_object_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use descriptions from custom schemas of nested objects") {
    val expectedYaml = loadYaml("expected_descriptions_in_nested_custom_schemas.yml")

    import SchemaType._
    implicit val customFruitAmountSchema: Schema[FruitAmount] = Schema(
      SProduct(
        SObjectInfo("tapir.tests.FruitAmount", Nil),
        List((FieldName("fruit"), Schema(SString)), (FieldName("amount"), Schema(SInteger).format("int32")))
      )
    ).description("Amount of fruits")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.post.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use descriptions from customised derived schemas") {
    val expectedYaml = loadYaml("expected_descriptions_in_nested_custom_schemas.yml")

    implicit val customFruitAmountSchema: Schema[FruitAmount] = implicitly[Derived[Schema[FruitAmount]]].value
      .description("Amount of fruits")
      .modifyUnsafe[Nothing]("amount")(_.format("int32"))

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.post.out(jsonBody[List[ObjectWrapper]]), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold coproducts from unfolded arrays") {
    val expectedYaml = loadYaml("expected_unfolded_coproduct_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[List[Entity]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic coproduct type is used multiple times") {
    val expectedYaml = loadYaml("expected_generic_coproduct.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        List(endpoint.in("p1" and jsonBody[GenericEntity[String]]), endpoint.in("p2" and jsonBody[GenericEntity[Int]])),
        Info("Fruits", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays from object") {
    val expectedYaml = loadYaml("expected_unfolded_array_unfolded_object.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[ObjectWithList]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed status code output in response") {
    val expectedYaml = loadYaml("expected_fixed_status_code.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(endpoint.out(statusCode(StatusCode.PermanentRedirect)).out(header[String]("Location")), Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use status codes declared with description") {
    val expectedYaml = loadYaml("expected_one_of_status_codes.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint
          .out(header[String]("Location"))
          .errorOut(statusCode.description(StatusCode.NotFound, "entity not found").description(StatusCode.BadRequest, "")),
        Info("Entities", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render additional properties for map") {
    val expectedYaml = loadYaml("expected_additional_properties.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[Map[String, Person]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render map with plain values") {
    val expectedYaml = loadYaml("expected_map_with_plain_values.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[Map[String, String]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  // #118
  test("use fixed status code output in response if it's the only output") {
    val expectedYaml = loadYaml("expected_fixed_status_code_2.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(statusCode(StatusCode.NoContent)), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support prepending inputs") {
    val expectedYaml = loadYaml("expected_prepended_input.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(in_query_query_out_string.in("add").prependIn("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("use fixed header output in response") {
    val expectedYaml = loadYaml("expected_fixed_header_output_response.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(header("Location", "Poland")), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed header input in request") {
    val expectedYaml = loadYaml("expected_fixed_header_input_request.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.in(header("Location", "Poland")), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("validator with tagged type in query") {
    val expectedYaml = loadYaml("expected_valid_query_tagged.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_query_tagged.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with wrapper type in body") {
    val expectedYaml = loadYaml("expected_valid_body_wrapped.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_valid_json.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with optional wrapper type in body") {
    val expectedYaml = loadYaml("expected_valid_optional_body_wrapped.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(Validation.in_valid_optional_json.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with enum type in body") {
    val expectedYaml = loadYaml("expected_valid_body_enum.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_json_wrapper_enum.in("add").in("path"), Info("Fruits", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with wrappers type in query") {
    val expectedYaml = loadYaml("expected_valid_query_wrapped.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_valid_query.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with list") {
    val expectedYaml = loadYaml("expected_valid_body_collection.yml")

    val actualYaml =
      OpenAPIDocsInterpreter.toOpenAPI(Validation.in_valid_json_collection.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("render validator for additional properties of map") {
    val expectedYaml = loadYaml("expected_valid_additional_properties.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_valid_map, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render validator for additional properties of array elements") {
    val expectedYaml = loadYaml("expected_valid_int_array.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_valid_int_array, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for classes") {
    val expectedYaml = loadYaml("expected_valid_enum_class.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_enum_class, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for classes wrapped in option") {
    val expectedYaml = loadYaml("expected_valid_enum_class_wrapped_in_option.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_optional_enum_class, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for values") {
    val expectedYaml = loadYaml("expected_valid_enum_values.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.in_enum_values, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use enum in object in output response") {
    val expectedYaml = loadYaml("expected_valid_enum_object.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(Validation.out_enum_object, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use enumeratum validator for array elements") {
    import sttp.tapir.codec.enumeratum._

    val expectedYaml = loadYaml("expected_valid_enumeratum.yml")

    val actualYaml =
      OpenAPIDocsInterpreter
        .toOpenAPI(List(endpoint.in("enum-test").out(jsonBody[Enumeratum.FruitWithEnum])), Info("Fruits", "1.0"))
        .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use enum validator for a cats non-empty-list of enums") {
    import sttp.tapir.integ.cats.codec._
    import cats.data.NonEmptyList
    implicit def schemaForColor: Schema[Color] =
      Schema.string.validate(Validator.enum(List(Blue, Red), { c => Some(c.toString.toLowerCase()) }))

    val expectedYaml = loadYaml("expected_valid_enum_cats_nel.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.in(jsonBody[NonEmptyList[Color]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support example of list and not-list types") {
    val expectedYaml = loadYaml("expected_examples_of_list_and_not_list_types.yml")
    val actualYaml = OpenAPIDocsInterpreter
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
    val expectedYaml = loadYaml("expected_multiple_examples_with_names.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .out(
            jsonBody[Entity].examples(
              List(
                Example.of(Person("michal", 40), Some("Michal"), Some("Some summary")),
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
    val expectedYaml = loadYaml("expected_multiple_examples_with_default_names.yml")
    val actualYaml = OpenAPIDocsInterpreter
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
    val expectedYaml = loadYaml("expected_single_example_with_name.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .out(
            jsonBody[Entity].example(
              Example(Person("michal", 40), Some("Michal"), Some("Some summary"))
            )
          ),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support multiple examples with both explicit and default names ") {
    val expectedYaml = loadYaml("expected_multiple_examples_with_explicit_and_default_names.yml")
    val actualYaml = OpenAPIDocsInterpreter
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
    val expectedYaml = loadYaml("expected_multiple_examples.yml")
    val actualYaml = OpenAPIDocsInterpreter
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

  test("support recursive coproducts") {
    val expectedYaml = loadYaml("expected_recursive_coproducts.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post.in(jsonBody[Clause]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render field validator when used inside of coproduct") {
    implicit val ageSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(11))
    val expectedYaml = loadYaml("expected_valid_coproduct.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.get.out(jsonBody[Entity]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render field validator when used inside of optional coproduct") {
    implicit val ageSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(11))
    val expectedYaml = loadYaml("expected_valid_optional_coproduct.yml")
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.get.in(jsonBody[Option[Entity]]), Info("Entities", "1.0")).toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("arbitrary json output") {
    val expectedYaml = loadYaml("expected_arbitrary_json_output.yml")
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
    val expectedYaml = loadYaml("expected_deprecated.yml")
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
    val expectedYaml = loadYaml("expected_array_no_format.yml")
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
    val expectedYaml = loadYaml("expected_single_server_with_variables.yml")

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
    val expectedYaml = loadYaml("expected_multiple_servers.yml")

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

  test("render field validator when using different naming configuration") {
    val expectedYaml = loadYaml("expected_validator_with_custom_naming.yml")

    implicit val customConfiguration: Configuration = Configuration.default.withSnakeCaseMemberNames
    val baseEndpoint = endpoint.post.in(jsonBody[MyClass])
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(baseEndpoint, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("automatically add example for fixed header") {
    val expectedYaml = loadYaml("expected_fixed_header_example.yml")

    val e = endpoint.in(header("Content-Type", "application/json"))
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use date-time format for Instant fields") {
    val expectedYaml = loadYaml("expected_date_time.yml")

    val e = endpoint.in(query[Instant]("instant"))
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("exclusive bounds") {
    val expectedYaml = loadYaml("expected_exclusive_bounds.yml")

    val qParam = query[Int]("num")
      .validate(Validator.min(0, exclusive = true))
      .validate(Validator.max(42, exclusive = true))
    val e = endpoint.in(qParam)
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use default for a query parameter") {
    val expectedYaml = loadYaml("expected_default_query_param.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post.in(query[String]("name").example("alan").default("tom")),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  private def loadYaml(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  private def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim
}

case class F1(data: List[F1])
case class G[T](data: T)

case class NestedEntity(entity: Entity)

sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case class Unauthorized(realm: String) extends ErrorInfo
case class Unknown(code: Int, msg: String) extends ErrorInfo

case class ObjectWrapper(value: FruitAmount)

sealed trait GenericEntity[T]
case class GenericPerson[T](data: T) extends GenericEntity[T]

case class ObjectWithList(data: List[FruitAmount])

sealed trait Clause
case class Expression(v: String) extends Clause
case class Not(not: Clause) extends Clause

case class MyClass(myAttribute: Int)
