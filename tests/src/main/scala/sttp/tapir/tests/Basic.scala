package sttp.tapir.tests

import io.circe.generic.auto._
import sttp.model.headers.{Cookie, CookieValueWithMeta, CookieWithMeta}
import sttp.model.{Header, MediaType, QueryParams, StatusCode}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.tests.data.{Entity, FruitAmount}

import java.io.InputStream
import java.nio.ByteBuffer

object Basic {
  val in_query_out_string: Endpoint[String, Unit, String, Any] = endpoint.in(query[String]("fruit")).out(stringBody)

  val in_query_out_infallible_string: Endpoint[String, Nothing, String, Any] =
    infallibleEndpoint.in(query[String]("fruit")).out(stringBody).name("infallible")

  val in_query_query_out_string: Endpoint[(String, Option[Int]), Unit, String, Any] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(stringBody)

  val in_header_out_string: Endpoint[String, Unit, String, Any] = endpoint.in(header[String]("X-Role")).out(stringBody)

  val in_path_path_out_string: Endpoint[(String, Int), Unit, String, Any] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(stringBody)

  val in_two_path_capture: Endpoint[(Int, Int), Unit, (Int, Int), Any] = endpoint
    .in("in" / path[Int] / path[Int])
    .out(header[Int]("a") and header[Int]("b"))

  val in_string_out_string: Endpoint[String, Unit, String, Any] = endpoint.post.in("api" / "echo").in(stringBody).out(stringBody)

  val in_path: Endpoint[String, Unit, Unit, Any] = endpoint.get.in("api").in(path[String])

  val in_fixed_header_out_string: Endpoint[Unit, Unit, String, Any] =
    endpoint.in("secret").in(header("location", "secret")).out(stringBody)

  val in_header_before_path: Endpoint[(String, Int), Unit, (Int, String), Any] = endpoint
    .in(header[String]("SomeHeader"))
    .in(path[Int])
    .out(header[Int]("IntHeader") and stringBody)

  val in_json_out_json: Endpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(jsonBody[FruitAmount])
      .out(jsonBody[FruitAmount])
      .name("echo json")

  val in_content_type_fixed_header: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(header(Header.contentType(MediaType.ApplicationJson)))

  val in_content_type_header_with_custom_decode_results: Endpoint[MediaType, Unit, Unit, Any] = {
    implicit val mediaTypeCodec: Codec[String, MediaType, CodecFormat.TextPlain] =
      Codec.string.mapDecode(_ => DecodeResult.Mismatch("", ""))(_.toString())
    endpoint.post
      .in("api" / "echo")
      .in(header[MediaType]("Content-Type"))
  }

  val in_byte_array_out_byte_array: Endpoint[Array[Byte], Unit, Array[Byte], Any] =
    endpoint.post.in("api" / "echo").in(byteArrayBody).out(byteArrayBody).name("echo byte array")

  val in_byte_buffer_out_byte_buffer: Endpoint[ByteBuffer, Unit, ByteBuffer, Any] =
    endpoint.post.in("api" / "echo").in(byteBufferBody).out(byteBufferBody).name("echo byte buffer")

  val in_input_stream_out_input_stream: Endpoint[InputStream, Unit, InputStream, Any] =
    endpoint.post.in("api" / "echo").in(inputStreamBody).out(inputStreamBody).name("echo input stream")

  val in_string_out_stream_with_header: Endpoint[String, Unit, (InputStream, Option[Long]), Any] =
    endpoint.post
      .in("api" / "echo")
      .in(stringBody)
      .out(inputStreamBody)
      .out(header[Option[Long]]("Content-Length"))
      .name("input string output stream with header")

  val in_unit_out_json_unit: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.in("api" / "unit").out(jsonBody[Unit])

  val in_unit_out_string: Endpoint[Unit, Unit, String, Any] =
    endpoint.in("api").out(stringBody)

  val in_unit_error_out_string: Endpoint[Unit, String, Unit, Any] =
    endpoint.in("api").errorOut(stringBody)

  val in_form_out_form: Endpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post.in("api" / "echo").in(formBody[FruitAmount]).out(formBody[FruitAmount])

  val in_query_params_out_string: Endpoint[QueryParams, Unit, String, Any] =
    endpoint.get.in("api" / "echo" / "params").in(queryParams).out(stringBody)

  val in_headers_out_headers: Endpoint[List[Header], Unit, List[Header], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(headers).out(headers)

  val in_json_out_headers: Endpoint[FruitAmount, Unit, List[Header], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(jsonBody[FruitAmount]).out(headers)

  val in_paths_out_string: Endpoint[List[String], Unit, String, Any] =
    endpoint.get.in(paths).out(stringBody)

  val in_path_paths_out_header_body: Endpoint[(Int, List[String]), Unit, (Int, String), Any] =
    endpoint.get.in("api").in(path[Int]).in("and").in(paths).out(header[Int]("IntPath") and stringBody)

  val in_path_fixed_capture_fixed_capture: Endpoint[(Int, Int), Unit, Unit, Any] =
    endpoint.get.in("customer" / path[Int]("customer_id") / "orders" / path[Int]("order_id"))

  val in_query_list_out_header_list: Endpoint[List[String], Unit, List[String], Any] =
    endpoint.get.in("api" / "echo" / "param-to-header").in(query[List[String]]("qq")).out(header[List[String]]("hh"))

  val in_cookie_cookie_out_header: Endpoint[(Int, String), Unit, List[String], Any] =
    endpoint.get
      .in("api" / "echo" / "headers")
      .in(cookie[Int]("c1"))
      .in(cookie[String]("c2"))
      .out(header[List[String]]("Set-Cookie"))

  val in_cookies_out_cookies: Endpoint[List[Cookie], Unit, List[CookieWithMeta], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(cookies).out(setCookies)

  val in_set_cookie_value_out_set_cookie_value: Endpoint[CookieValueWithMeta, Unit, CookieValueWithMeta, Any] =
    endpoint.get.in("api" / "echo" / "headers").in(setCookie("c1")).out(setCookie("c1"))

  val in_query_out_cookie_raw: Endpoint[String, Unit, (String, String), Any] =
    endpoint.get.in("").in(query[String]("q")).out(header[String]("Set-Cookie")).out(stringBody)

  val in_root_path: Endpoint[Unit, Unit, Unit, Any] = endpoint.get.in("")

  val in_single_path: Endpoint[Unit, Unit, Unit, Any] = endpoint.get.in("api")

  val in_extract_request_out_string: Endpoint[String, Unit, String, Any] =
    endpoint.in(extractFromRequest(_.method.method)).out(stringBody)

  val in_string_out_status: Endpoint[String, Unit, StatusCode, Any] =
    endpoint.in(query[String]("fruit")).out(statusCode)

  val delete_endpoint: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.delete.in("api" / "delete").out(statusCode(StatusCode.Ok).description("ok"))

  val in_string_out_content_type_string: Endpoint[String, Unit, (String, String), Any] =
    endpoint.in("api" / "echo").in(stringBody).out(stringBody).out(header[String]("Content-Type"))

  val in_content_type_out_string: Endpoint[String, Unit, String, Any] =
    endpoint.in("api" / "echo").in(header[String]("Content-Type")).out(stringBody)

  val in_unit_out_html: Endpoint[Unit, Unit, String, Any] =
    endpoint.in("api" / "echo").out(htmlBodyUtf8)

  val in_unit_out_header_redirect: Endpoint[Unit, Unit, String, Any] =
    endpoint.out(statusCode(StatusCode.PermanentRedirect)).out(header[String]("Location"))

  val in_unit_out_fixed_header: Endpoint[Unit, Unit, Unit, Any] =
    endpoint.out(header("Location", "Poland"))

  val in_optional_json_out_optional_json: Endpoint[Option[FruitAmount], Unit, Option[FruitAmount], Any] =
    endpoint.post.in("api" / "echo").in(jsonBody[Option[FruitAmount]]).out(jsonBody[Option[FruitAmount]])

  val in_optional_coproduct_json_out_optional_coproduct_json: Endpoint[Option[Entity], Unit, Option[Entity], Any] =
    endpoint.post.in("api" / "echo" / "coproduct").in(jsonBody[Option[Entity]]).out(jsonBody[Option[Entity]])

  val not_existing_endpoint: Endpoint[Unit, String, Unit, Any] =
    endpoint.in("api" / "not-existing").errorOut(oneOf(oneOfMapping(StatusCode.BadRequest, stringBody)))

  val in_query_with_default_out_string: Endpoint[String, Unit, String, Any] =
    endpoint
      .in(query[String]("p1").default("DEFAULT"))
      .out(stringBody)
      .name("Query with default")

  val out_fixed_content_type_header: Endpoint[Unit, Unit, String, Any] =
    endpoint.out(stringBody.and(header("Content-Type", "text/csv")))
}
