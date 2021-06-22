package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.{endpoint, _}
import sttp.tapir.generic.{Configuration, Derived}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.tests._
import sttp.tapir.docs.openapi.dtos.VerifyYamlValidatorTestData._

class VerifyYamlValidatorTest extends AnyFunSuite with Matchers {

  test("validator with tagged type in query") {
    val expectedYaml = load("validator/expected_valid_query_tagged.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_query_tagged.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with wrapper type in body") {
    val expectedYaml = load("validator/expected_valid_body_wrapped.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_valid_json.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with optional wrapper type in body") {
    val expectedYaml = load("validator/expected_valid_optional_body_wrapped.yml")

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(Validation.in_valid_optional_json.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with enum type in body") {
    val expectedYaml = load("validator/expected_valid_body_enum.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_json_wrapper_enum.in("add").in("path"), Info("Fruits", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with wrappers type in query") {
    val expectedYaml = load("validator/expected_valid_query_wrapped.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_valid_query.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("validator with list") {
    val expectedYaml = load("validator/expected_valid_body_collection.yml")

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(Validation.in_valid_json_collection.in("add").in("path"), Info("Fruits", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("render validator for additional properties of map") {
    val expectedYaml = load("validator/expected_valid_additional_properties.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_valid_map, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render validator for additional properties of array elements") {
    val expectedYaml = load("validator/expected_valid_int_array.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_valid_int_array, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for classes") {
    val expectedYaml = load("validator/expected_valid_enum_class.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_enum_class, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for classes wrapped in option") {
    val expectedYaml = load("validator/expected_valid_enum_class_wrapped_in_option.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_optional_enum_class, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render enum validator for values") {
    val expectedYaml = load("validator/expected_valid_enum_values.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.in_enum_values, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use enumeratum validator for array elements") {
    import sttp.tapir.codec.enumeratum._

    val expectedYaml = load("validator/expected_valid_enumeratum.yml")

    val actualYaml =
      OpenAPIDocsInterpreter()
        .toOpenAPI(List(endpoint.in("enum-test").out(jsonBody[Enumeratum.FruitWithEnum])), Info("Fruits", "1.0"))
        .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use enum validator for a cats non-empty-list of enums") {
    import cats.data.NonEmptyList
    import sttp.tapir.integ.cats.codec._
    implicit def schemaForColor: Schema[Color] =
      Schema.string.validate(Validator.enumeration(List(Blue, Red), { c => Some(c.toString.toLowerCase()) }))

    val expectedYaml = load("validator/expected_valid_enum_cats_nel.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.in(jsonBody[NonEmptyList[Color]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render field validator when used inside of coproduct") {
    implicit val ageSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(11))
    val expectedYaml = load("validator/expected_valid_coproduct.yml")
    val actualYaml = OpenAPIDocsInterpreter()
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
    val expectedYaml = load("validator/expected_valid_optional_coproduct.yml")
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.get.in(jsonBody[Option[Entity]]), Info("Entities", "1.0")).toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("render field validator when using different naming configuration") {
    val expectedYaml = load("validator/expected_validator_with_custom_naming.yml")

    implicit val customConfiguration: Configuration = Configuration.default.withSnakeCaseMemberNames
    val baseEndpoint = endpoint.post.in(jsonBody[MyClass])
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(baseEndpoint, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should include max items in modified string collection schema") {
    val expectedYaml = load("validator/expected_valid_modified_array_strings.yml")

    implicit val customObjectWithStringsSchema: Schema[ObjectWithStrings] = implicitly[Derived[Schema[ObjectWithStrings]]].value
      .modify(_.data)(_.validate(Validator.maxSize[String, List](1)))

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.out(jsonBody[ObjectWithStrings]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should include max items in modified object collection schema") {
    val expectedYaml = load("validator/expected_valid_modified_array_objects.yml")

    implicit val customObjectWithStringsSchema: Schema[ObjectWithList] = implicitly[Derived[Schema[ObjectWithList]]].value
      .modify(_.data)(_.validate(Validator.maxSize[FruitAmount, List](1)))

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint.out(jsonBody[ObjectWithList]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use enum in object in output response") {
    val expectedYaml = load("validator/expected_valid_enum_object.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(Validation.out_enum_object, Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

}
