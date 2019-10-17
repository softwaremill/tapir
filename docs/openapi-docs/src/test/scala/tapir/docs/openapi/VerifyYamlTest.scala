package tapir.docs.openapi

import com.github.ghik.silencer.silent
import io.circe.generic.auto._
import org.scalatest.{FunSuite, Matchers}
import tapir._
import tapir.docs.openapi.dtos.Book
import tapir.docs.openapi.dtos.a.{Pet => APet}
import tapir.docs.openapi.dtos.b.{Pet => BPet}
import tapir.json.circe._
import tapir.model.{Method, StatusCodes}
import tapir.openapi.circe.yaml._
import tapir.openapi.{Contact, Info, License}
import tapir.tests.{FruitAmount, _}

import scala.io.Source

class VerifyYamlTest extends FunSuite with Matchers {

  val all_the_way: Endpoint[(FruitAmount, String), Unit, (FruitAmount, Int), Nothing] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(jsonBody[FruitAmount])
    .out(header[Int]("X-Role"))

  test("should match the expected yaml") {
    val expectedYaml = loadYaml("expected.yml")

    val actualYaml = List(in_query_query_out_string, all_the_way, delete_endpoint).toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  val endpoint_wit_recursive_structure: Endpoint[Unit, Unit, F1, Nothing] = endpoint
    .out(jsonBody[F1])

  test("should match the expected yaml when schema is recursive") {
    val expectedYaml = loadYaml("expected_recursive.yml")

    val actualYaml = endpoint_wit_recursive_structure.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use custom operationId generator") {
    def customOperationIdGenerator(pc: Vector[String], m: Method) = pc.map(_.toUpperCase).mkString("", "+", "-") + m.m.toUpperCase
    val options = OpenAPIDocsOptions.default.copy(customOperationIdGenerator)
    val expectedYaml = loadYaml("expected_custom_operation_id.yml")

    val actualYaml = in_query_query_out_string
      .in("add")
      .in("path")
      .toOpenAPI(Info("Fruits", "1.0"))(options)
      .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  val streaming_endpoint: Endpoint[Vector[Byte], Unit, Vector[Byte], Vector[Byte]] = endpoint
    .in(streamBody[Vector[Byte]](schemaFor[String], MediaType.TextPlain()))
    .out(streamBody[Vector[Byte]](Schema.SBinary, MediaType.OctetStream()))

  test("should match the expected yaml for streaming endpoints") {
    val expectedYaml = loadYaml("expected_streaming.yml")

    val actualYaml = streaming_endpoint.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support tags") {
    val userTaggedEndpointShow = endpoint.tag("user").in("user" / "show").get.out(plainBody[String])
    val userTaggedEdnpointSearch = endpoint.tags(List("user", "admin")).in("user" / "search").get.out(plainBody[String])
    val adminTaggedEndpointAdd = endpoint.tag("admin").in("admin" / "add").get.out(plainBody[String])

    val expectedYaml = loadYaml("expected_tags.yml")

    val actualYaml = List(userTaggedEndpointShow, userTaggedEdnpointSearch, adminTaggedEndpointAdd).toOpenAPI(Info("Fruits", "1.0")).toYaml
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

    val actualYaml = in_query_query_out_string.toOpenAPI(api).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multipart") {
    val expectedYaml = loadYaml("expected_multipart.yml")

    val actualYaml = List(in_file_multipart_out_multipart).toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support authentication") {
    val expectedYaml = loadYaml("expected_auth.yml")

    val e1 = endpoint.in(auth.bearer).in("api1" / path[String]).out(stringBody)
    val e2 = endpoint.in(auth.bearer).in("api2" / path[String]).out(stringBody)
    val e3 = endpoint.in(auth.apiKey(header[String]("apikey"))).in("api3" / path[String]).out(stringBody)

    val actualYaml = List(e1, e2, e3).toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support empty bodies") {
    val expectedYaml = loadYaml("expected_empty.yml")

    val actualYaml = List(endpoint).toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple status codes") {
    // given
    val expectedYaml = loadYaml("expected_status_codes.yml")

    // work-around for #10: unsupported sealed trait families
    @silent("never used") // it is used
    implicit val schemaForErrorInfo: SchemaFor[ErrorInfo] = new SchemaFor[ErrorInfo] {
      override def schema: Schema = Schema.SProduct(Schema.SObjectInfo("ErrorInfo"), Nil, Nil)
    }

    val e = endpoint.errorOut(
      tapir.oneOf(
        statusMapping(StatusCodes.NotFound, jsonBody[NotFound].description("not found")),
        statusMapping(StatusCodes.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
        statusDefaultMapping(jsonBody[Unknown].description("unknown"))
      )
    )

    // when
    val actualYaml = List(e).toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    // then
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should keep the order of multiple endpoints") {
    val expectedYaml = loadYaml("expected_multiple.yml")

    val actualYaml = List(endpoint.in("p1"), endpoint.in("p3"), endpoint.in("p2"), endpoint.in("p5"), endpoint.in("p4"))
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types") {
    val expectedYaml = loadYaml("expected_coproduct.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Entity, Nothing] = endpoint
      .out(jsonBody[Entity])

    val actualYaml = endpoint_wit_sealed_trait.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types with discriminator") {
    val sPerson = implicitly[SchemaFor[Person]]
    val sOrganization = implicitly[SchemaFor[Organization]]
    implicit val sEntity: SchemaFor[Entity] = SchemaFor.oneOf[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = loadYaml("expected_coproduct_discriminator.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Entity, Nothing] = endpoint
      .out(jsonBody[Entity])
    val actualYaml = endpoint_wit_sealed_trait.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types") {
    val expectedYaml = loadYaml("expected_coproduct_nested.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, NestedEntity, Nothing] = endpoint
      .out(jsonBody[NestedEntity])

    val actualYaml = endpoint_wit_sealed_trait.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types with discriminator") {
    val sPerson = implicitly[SchemaFor[Person]]
    val sOrganization = implicitly[SchemaFor[Organization]]
    @silent("never used") // it is used
    implicit val sEntity: SchemaFor[Entity] = SchemaFor.oneOf[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = loadYaml("expected_coproduct_discriminator_nested.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, NestedEntity, Nothing] = endpoint
      .out(jsonBody[NestedEntity])
    val actualYaml = endpoint_wit_sealed_trait.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should handle classes with same name") {
    val e: Endpoint[APet, Unit, BPet, Nothing] = endpoint
      .in(jsonBody[APet])
      .out(jsonBody[BPet])
    val expectedYaml = loadYaml("expected_same_fullnames.yml")

    val actualYaml = e.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold nested hierarchy") {
    val e: Endpoint[Book, Unit, String, Nothing] = endpoint
      .in(jsonBody[Book])
      .out(plainBody[String])
    val expectedYaml = loadYaml("expected_unfolded_hierarchy.yml")

    val actualYaml = e.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays") {
    val e = endpoint.in(jsonBody[List[FruitAmount]]).out(plainBody[String])
    val expectedYaml = loadYaml("expected_unfolded_array.yml")

    val actualYaml = e.toOpenAPI(Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic type is used multiple times") {
    val expectedYaml = loadYaml("expected_generic.yml")

    val actualYaml = List(endpoint.in("p1" and jsonBody[G[String]]), endpoint.in("p2" and jsonBody[G[Int]]))
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold objects from unfolded arrays") {
    val expectedYaml = loadYaml("expected_unfolded_object_unfolded_array.yml")

    val actualYaml = endpoint
      .out(jsonBody[List[ObjectWrapper]])
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold coproducts from unfolded arrays") {
    val expectedYaml = loadYaml("expected_unfolded_coproduct_unfolded_array.yml")

    val actualYaml = endpoint
      .out(jsonBody[List[Entity]])
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic coproduct type is used multiple times") {
    val expectedYaml = loadYaml("expected_generic_coproduct.yml")

    val actualYaml = List(endpoint.in("p1" and jsonBody[GenericEntity[String]]), endpoint.in("p2" and jsonBody[GenericEntity[Int]]))
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold arrays from object") {
    val expectedYaml = loadYaml("expected_unfolded_array_unfolded_object.yml")

    val actualYaml = endpoint
      .out(jsonBody[ObjectWithList])
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed status code output in response") {
    val expectedYaml = loadYaml("expected_fixed_status_code.yml")

    val actualYaml = endpoint
      .out(statusCode(StatusCodes.PermanentRedirect))
      .out(header[String]("Location"))
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render additional properties for map") {
    val expectedYaml = loadYaml("expected_additional_properties.yml")

    val actualYaml = endpoint
      .out(jsonBody[Map[String, Person]])
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render map with plain values") {
    val expectedYaml = loadYaml("expected_map_with_plain_values.yml")

    val actualYaml = endpoint
      .out(jsonBody[Map[String, String]])
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  // #118
  test("use fixed status code output in response if it's the only output") {
    val expectedYaml = loadYaml("expected_fixed_status_code_2.yml")

    val actualYaml = endpoint
      .out(statusCode(StatusCodes.NoContent))
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support prepending inputs") {
    val expectedYaml = loadYaml("expected_prepended_input.yml")

    val actualYaml = in_query_query_out_string
      .in("add")
      .prependIn("path")
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("use fixed header output in response") {
    val expectedYaml = loadYaml("expected_fixed_header_output_response.yml")

    val actualYaml = endpoint
      .out(header("Location", "Poland"))
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use fixed header input in request") {
    val expectedYaml = loadYaml("expected_fixed_header_input_request.yml")

    val actualYaml = endpoint
      .in(header("Location", "Poland"))
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("validator with tagged type in query") {
    val expectedYaml = loadYaml("expected_valid_query_tagged.yml")

    val actualYaml = Validation.in_query_tagged
      .in("add")
      .in("path")
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with wrapper type in body") {
    val expectedYaml = loadYaml("expected_valid_body_wrapped.yml")

    val actualYaml = Validation.in_json_wrapper
      .in("add")
      .in("path")
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with enum type in body") {
    val expectedYaml = loadYaml("expected_valid_body_enum.yml")

    val actualYaml = Validation.in_json_wrapper_enum
      .in("add")
      .in("path")
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with wrappers type in query") {
    val expectedYaml = loadYaml("expected_valid_query_wrapped.yml")

    val actualYaml = Validation.in_query_wrapper
      .in("add")
      .in("path")
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with list") {
    val expectedYaml = loadYaml("expected_valid_body_collection.yml")

    val actualYaml = Validation.in_json_collection
      .in("add")
      .in("path")
      .toOpenAPI(Info("Fruits", "1.0"))
      .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("render validator for additional properties of map") {
    val expectedYaml = loadYaml("expected_valid_additional_properties.yml")

    val actualYaml = Validation.in_map
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for classes") {
    val expectedYaml = loadYaml("expected_valid_enum_class.yml")

    val actualYaml = Validation.in_enum_class
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for classes wrapped in option") {
    val expectedYaml = loadYaml("expected_valid_enum_class_wrapped_in_option.yml")

    val actualYaml = Validation.in_optional_enum_class
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for values") {
    val expectedYaml = loadYaml("expected_valid_enum_values.yml")

    val actualYaml = Validation.in_enum_values
      .toOpenAPI(Info("Entities", "1.0"))
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use enum in object in output response") {
    val expectedYaml = loadYaml("expected_valid_enum_object.yml")

    val actualYaml = Validation.out_enum_object
      .toOpenAPI(Info("Entities", "1.0"))
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

sealed trait Entity {
  def name: String
}
case class Person(name: String, age: Int) extends Entity
case class Organization(name: String) extends Entity

sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case class Unauthorized(realm: String) extends ErrorInfo
case class Unknown(code: Int, msg: String) extends ErrorInfo

case class ObjectWrapper(value: FruitAmount)

sealed trait GenericEntity[T]
case class GenericPerson[T](data: T) extends GenericEntity[T]

case class ObjectWithList(data: List[FruitAmount])
