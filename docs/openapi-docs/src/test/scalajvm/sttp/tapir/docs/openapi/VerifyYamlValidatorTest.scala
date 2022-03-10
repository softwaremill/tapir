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
import sttp.tapir.tests.{data, _}
import sttp.tapir.docs.openapi.dtos.VerifyYamlValidatorTestData._
import sttp.tapir.tests.data.{Entity, FruitAmount}

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

  test("use enum validator for a cats non-empty-list of enums") {
    import cats.data.NonEmptyList
    import sttp.tapir.integ.cats.codec._
    implicit def schemaForColor: Schema[data.Color] =
      Schema.string.validate(Validator.enumeration(List(data.Blue, data.Red), { c => Some(c.toString.toLowerCase()) }))

    val expectedYaml = load("validator/expected_valid_enum_cats_nel.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpoint.in(jsonBody[NonEmptyList[data.Color]]), Info("Entities", "1.0")).toYaml
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

  test("should infer the encode-to-raw function if one isn't provided for primitive valued enumerations") {
    val expectedYaml = load("validator/expected_validator_infer_encode_to_raw.yml")

    val getEnums = endpoint.get
      .in("enums")
      .in(query[List[Int]]("ns").validateIterable(Validator.enumeration(List(1, 2))))
      .out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(getEnums), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should add encode to a validator using the codec in scope") {
    val expectedYaml = load("validator/expected_validator_encode_from_codec.yml")

    case class A()
    implicit val aPlainCodec: Codec.PlainCodec[A] = Codec.string.map(_ => A())(_ => "AA")

    val getEnums = endpoint.get
      .in("enums")
      .in(query[List[A]]("as").validateIterable(Validator.enumeration(List(A())).encodeWithPlainCodec))
      .out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(getEnums), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use whole numbers in examples of string-schema whole-number numeric types") {
    val expectedYaml = load("validator/expected_whole_numbers_in_examples_of_string_schemas.yml")

    val positive = endpoint.get
      .in("positive")
      .in(query[BigInt]("x").validate(Validator.min(0)))

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(positive), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }
}
