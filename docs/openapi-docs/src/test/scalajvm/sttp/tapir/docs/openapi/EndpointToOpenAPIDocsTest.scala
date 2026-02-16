package sttp.tapir.docs.openapi

import sttp.apispec.openapi.Info
import sttp.tapir.tests._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.endpoint
import sttp.tapir.AnyEndpoint
import sttp.tapir.tests.Security._
import sttp.tapir.tests.Basic._
import sttp.tapir.tests.Files._
import sttp.tapir.tests.Mapping._
import sttp.tapir.tests.Multipart._
import sttp.tapir.tests.OneOf._
import io.circe.generic.auto._
import sttp.tapir.docs.openapi.dtos.a.{Pet => APet}
import sttp.tapir.docs.openapi.dtos.b.{Pet => BPet}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

class EndpointToOpenAPIDocsTest extends AnyFunSuite with Matchers {

  val allTestEndpoints: List[AnyEndpoint] = List(
    in_query_out_string,
    in_query_out_infallible_string,
    in_query_query_out_string,
    in_header_out_string,
    in_path_path_out_string,
    in_two_path_capture,
    in_string_out_string,
    in_path,
    in_fixed_header_out_string,
    in_mapped_query_out_string,
    in_mapped_path_out_string,
    in_mapped_path_path_out_string,
    in_query_mapped_path_path_out_string,
    in_query_out_mapped_string,
    in_query_out_mapped_string_header,
    in_header_before_path,
    in_json_out_json,
    in_content_type_fixed_header,
    in_content_type_header_with_custom_decode_results,
    in_byte_array_out_byte_array,
    in_byte_buffer_out_byte_buffer,
    in_input_stream_out_input_stream,
    in_string_out_stream_with_header,
    in_file_out_file,
    in_unit_out_json_unit,
    in_unit_out_string,
    in_unit_error_out_string,
    in_form_out_form,
    in_query_params_out_string,
    in_headers_out_headers,
    in_json_out_headers,
    in_paths_out_string,
    in_path_paths_out_header_body,
    in_path_fixed_capture_fixed_capture,
    in_query_list_out_header_list,
    in_simple_multipart_out_multipart,
    in_simple_multipart_out_string,
    in_simple_multipart_out_raw_string,
    in_file_multipart_out_multipart,
    in_raw_multipart_out_string,
    in_cookie_cookie_out_header,
    in_cookies_out_cookies,
    in_set_cookie_value_out_set_cookie_value,
    in_root_path,
    in_single_path,
    in_extract_request_out_string,
    in_security_apikey_header_out_string,
    in_security_apikey_query_out_string,
    in_security_basic_out_string,
    in_security_bearer_out_string,
    in_string_out_status_from_string,
    in_int_out_value_form_exact_match,
    in_string_out_status_from_type_erasure_using_partial_matcher,
    in_string_out_status_from_string_one_empty,
    in_string_out_status,
    delete_endpoint,
    in_string_out_content_type_string,
    in_content_type_out_string,
    in_unit_out_html,
    in_unit_out_header_redirect,
    in_unit_out_fixed_header,
    in_optional_json_out_optional_json,
    in_optional_coproduct_json_out_optional_coproduct_json,
    not_existing_endpoint,
    in_header_out_header_unit_extended,
    in_4query_out_4header_extended,
    in_3query_out_3header_mapped_to_tuple,
    in_2query_out_2query_mapped_to_unit,
    in_query_with_default_out_string,
    out_fixed_content_type_header,
    out_json_or_default_json,
    out_no_content_or_ok_empty_output,
    out_json_or_empty_output_no_content,
    ContentNegotiation.out_json_xml_text_common_schema,
    ContentNegotiation.out_json_xml_different_schema,
    Validation.in_query_tagged,
    Validation.in_query,
    Validation.in_valid_json,
    Validation.in_valid_optional_json,
    Validation.in_valid_query,
    Validation.in_valid_json_collection,
    Validation.in_valid_map,
    Validation.in_enum_class,
    Validation.in_optional_enum_class,
    Validation.out_enum_object,
    Validation.in_enum_values,
    Validation.in_json_wrapper_enum,
    Validation.in_valid_int_array
  )

  for (e <- allTestEndpoints) {
    test(s"${e.showDetail} should convert to open api") {
      OpenAPIDocsInterpreter().toOpenAPI(e, Info("title", "19.2-beta-RC1"))
    }
  }

  test("should fail when OpenAPIDocsOptions.failOnDuplicateOperationId is true and there are duplicate operationIds") {
    val e1 = endpoint.get.name("a")
    val e2 = endpoint.post.name("a")

    val options = OpenAPIDocsOptions.default.copy(failOnDuplicateOperationId = true)

    val es = List(e1, e2)

    assertThrows[IllegalStateException](
      OpenAPIDocsInterpreter(options).toOpenAPI(es, Info("title", "19.2-beta-RC1"))
    )
  }
  test("should pass when OpenAPIDocsOptions.failOnDuplicateOperationId is false and there are duplicate operationIds") {
    val e1 = endpoint.get.name("a")
    val e2 = endpoint.post.name("a")

    val options = OpenAPIDocsOptions.default.copy(failOnDuplicateOperationId = false)

    val es = List(e1, e2)

    OpenAPIDocsInterpreter(options).toOpenAPI(es, Info("title", "19.2-beta-RC1"))
  }

  test("should fail when OpenAPIDocsOptions.failOnDuplicateSchemaName is true and there are duplicate schema names") {
    val e: sttp.tapir.Endpoint[Unit, APet, Unit, BPet, Any] = endpoint
      .in(jsonBody[APet])
      .out(jsonBody[BPet])

    val options = OpenAPIDocsOptions.default.copy(failOnDuplicateSchemaName = true)

    val thrown = intercept[IllegalStateException] {
      OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("Entities", "1.0"))
    }

    thrown.getMessage should include("Duplicate schema names found")
    thrown.getMessage should include("Pet")
  }

  test("should pass when OpenAPIDocsOptions.failOnDuplicateSchemaName is false and there are duplicate schema names") {
    val e: sttp.tapir.Endpoint[Unit, APet, Unit, BPet, Any] = endpoint
      .in(jsonBody[APet])
      .out(jsonBody[BPet])

    val options = OpenAPIDocsOptions.default.copy(failOnDuplicateSchemaName = false)

    OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("Entities", "1.0"))
  }
}
