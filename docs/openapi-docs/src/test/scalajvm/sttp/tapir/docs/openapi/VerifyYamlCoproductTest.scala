package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir._
import sttp.tapir.docs.openapi.dtos.VerifyYamlCoproductTestData._
import sttp.tapir.docs.openapi.dtos.VerifyYamlCoproductTestData2._
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.tests.data.{Entity, Organization, Person}

class VerifyYamlCoproductTest extends AnyFunSuite with Matchers {
  test("should match expected yaml for coproduct with enum field") {
    implicit val shapeCodec: io.circe.Codec[Shape] = null
    implicit val schema: Schema[Shape] = Schema
      .oneOfUsingField[Shape, String](
        _.shapeType,
        identity
      )(
        Square.toString() -> implicitly[Schema[Square]]
      )

    val endpoint = sttp.tapir.endpoint.get.out(jsonBody[Shape])

    val expectedYaml = load("coproduct/expected_coproduct_discriminator_with_enum_circe.yml")
    val actualYaml =
      OpenAPIDocsInterpreter()
        .toOpenAPI(endpoint, "My Bookshop", "1.0")
        .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types") {
    val expectedYaml = load("coproduct/expected_coproduct.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Unit, Entity, Any] = endpoint
      .out(jsonBody[Entity])

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types with discriminator") {
    val sPerson = implicitly[Schema[Person]]
    val sOrganization = implicitly[Schema[Organization]]
    implicit val sEntity: Schema[Entity] =
      Schema.oneOfUsingField[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = load("coproduct/expected_coproduct_discriminator.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Unit, Entity, Any] = endpoint
      .out(jsonBody[Entity])
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types") {
    val expectedYaml = load("coproduct/expected_coproduct_nested.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Unit, NestedEntity, Any] = endpoint
      .out(jsonBody[NestedEntity])

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types with discriminator") {
    val sPerson = implicitly[Schema[Person]]
    val sOrganization = implicitly[Schema[Organization]]
    implicit val sEntity: Schema[Entity] =
      Schema.oneOfUsingField[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = load("coproduct/expected_coproduct_discriminator_nested.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Unit, NestedEntity, Any] = endpoint
      .out(jsonBody[NestedEntity])
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold coproducts from unfolded arrays") {
    val expectedYaml = load("coproduct/expected_unfolded_coproduct_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.out(jsonBody[List[Entity]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic coproduct type is used multiple times") {
    val expectedYaml = load("coproduct/expected_generic_coproduct.yml")

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        List(endpoint.in("p1" and jsonBody[GenericEntity[String]]), endpoint.in("p2" and jsonBody[GenericEntity[Int]])),
        Info("Fruits", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support recursive coproducts") {
    val expectedYaml = load("coproduct/expected_recursive_coproducts.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.post.in(jsonBody[Clause]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("flat either schema") {
    // using the left-biased codec, instead of the default right-biased one.
    implicit def eitherCodec[L, A, B, CF <: CodecFormat](implicit c1: Codec[L, A, CF], c2: Codec[L, B, CF]): Codec[L, Either[A, B], CF] =
      Codec.eitherLeft(c1, c2)
    val ep = endpoint.in(query[Either[Int, String]]("q"))

    val expectedYaml = load("coproduct/expected_flat_either.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(ep, "title", "1.0").toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // https://github.com/softwaremill/tapir/issues/2358
  test("coproduct elements used individually should have separate schemas when there's a discriminator") {
    implicit val tapirSchemaConfiguration: Configuration = Configuration.default.withDiscriminator("kind").withKebabCaseDiscriminatorValues

    val personEndpoint = endpoint.get
      .in("api" / "user" / path[String]("userId"))
      .in(jsonBody[Person])

    val entityEndpoint = endpoint.get
      .in("api" / "entity" / path[String]("entityId"))
      .in(jsonBody[Entity])

    val expectedYaml = load("coproduct/expected_coproduct_independent.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(personEndpoint, entityEndpoint), "title", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("coproduct using a wrapped representation of child schemas") {
    implicit val entitySchema: Schema[Entity] = Schema.oneOfWrapped[Entity]

    val entityEndpoint = endpoint.get
      .in("api" / "entity" / path[String]("entityId"))
      .in(jsonBody[Entity])

    val expectedYaml = load("coproduct/expected_coproduct_wrapped.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(entityEndpoint), "title", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("coproduct using an enum discriminator, added using `.addDiscriminatorField`") {
    val petEndpoint = endpoint.post
      .in("figure")
      .in(jsonBody[Pet])

    val expectedYaml = load("coproduct/expected_coproduct_discriminator_enum.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(petEndpoint), "title", "1.0").toYaml
    println(actualYaml)

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}
