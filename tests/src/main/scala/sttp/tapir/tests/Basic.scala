package sttp.tapir.tests

import io.circe.generic.auto._
import sttp.model.headers.{Cookie, CookieValueWithMeta, CookieWithMeta}
import sttp.model.{Header, HeaderNames, MediaType, QueryParams, StatusCode}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.tests.data.{Entity, FruitAmount}

import java.io.InputStream
import java.nio.ByteBuffer

object Basic {
  val in_query_out_string: PublicEndpoint[String, Unit, String, Any] = endpoint.in(query[String]("fruit")).out(stringBody)

  val in_query_out_infallible_string: PublicEndpoint[String, Nothing, String, Any] =
    infallibleEndpoint.in(query[String]("fruit")).out(stringBody).name("infallible")

  val in_query_query_out_string: PublicEndpoint[(String, Option[Int]), Unit, String, Any] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(stringBody)

  val in_header_out_string: PublicEndpoint[String, Unit, String, Any] = endpoint.in(header[String]("X-Role")).out(stringBody)

  val in_path_path_out_string: PublicEndpoint[(String, Int), Unit, String, Any] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(stringBody)

  val in_two_path_capture: PublicEndpoint[(Int, Int), Unit, (Int, Int), Any] = endpoint
    .in("in" / path[Int] / path[Int])
    .out(header[Int]("a") and header[Int]("b"))

  val in_string_out_string: PublicEndpoint[String, Unit, String, Any] = endpoint.post.in("api" / "echo").in(stringBody).out(stringBody)

  val in_path: PublicEndpoint[String, Unit, Unit, Any] = endpoint.get.in("api").in(path[String])

  val in_fixed_header_out_string: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("secret").in(header("location", "secret")).out(stringBody)

  val in_fixed_content_type_header_out_string: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("api").in(header(Header.contentType(MediaType.MultipartFormData))).out(stringBody)

  val in_header_before_path: PublicEndpoint[(String, Int), Unit, (Int, String), Any] = endpoint
    .in(header[String]("SomeHeader"))
    .in(path[Int])
    .out(header[Int]("IntHeader") and stringBody)

  val in_json_out_json: PublicEndpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(jsonBody[FruitAmount])
      .out(jsonBody[FruitAmount])
      .name("echo json")

  val in_content_type_fixed_header: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(header(Header.contentType(MediaType.ApplicationJson)))

  val in_content_type_header_with_custom_decode_results: PublicEndpoint[MediaType, Unit, Unit, Any] = {
    implicit val mediaTypeCodec: Codec[String, MediaType, CodecFormat.TextPlain] =
      Codec.string.mapDecode(_ => DecodeResult.Mismatch("", ""))(_.toString())
    endpoint.post
      .in("api" / "echo")
      .in(header[MediaType]("Content-Type"))
  }

  val in_string_out_string_media_type_json: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("api" / "json")
      .in(stringBody)
      .out(stringBody)
      .out(header(Header.contentType(MediaType.ApplicationJson)))

  val in_string_out_string_media_type_json_body: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("api" / "json_body")
      .in(stringBody)
      .out(stringJsonBody)

  val in_byte_array_out_byte_array: PublicEndpoint[Array[Byte], Unit, Array[Byte], Any] =
    endpoint.post.in("api" / "echo").in(byteArrayBody).out(byteArrayBody).name("echo byte array")

  val in_byte_buffer_out_byte_buffer: PublicEndpoint[ByteBuffer, Unit, ByteBuffer, Any] =
    endpoint.post.in("api" / "echo").in(byteBufferBody).out(byteBufferBody).name("echo byte buffer")

  val in_input_stream_out_input_stream: PublicEndpoint[InputStream, Unit, InputStream, Any] =
    endpoint.post.in("api" / "echo").in(inputStreamBody).out(inputStreamBody).name("echo input stream")

  val in_string_out_stream_with_header: PublicEndpoint[String, Unit, (InputStream, Option[Long]), Any] =
    endpoint.post
      .in("api" / "echo")
      .in(stringBody)
      .out(inputStreamBody)
      .out(header[Option[Long]]("Content-Length"))
      .name("input string output stream with header")

  val in_unit_out_json_unit: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.in("api" / "unit").out(jsonBody[Unit])

  val in_unit_out_string: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("api").out(stringBody)

  val in_unit_error_out_string: PublicEndpoint[Unit, String, Unit, Any] =
    endpoint.in("api").errorOut(stringBody)

  val in_form_out_form: PublicEndpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post.in("api" / "echo").in(formBody[FruitAmount]).out(formBody[FruitAmount])

  val in_query_params_out_string: PublicEndpoint[QueryParams, Unit, String, Any] =
    endpoint.get.in("api" / "echo" / "params").in(queryParams).out(stringBody)

  val in_headers_out_headers: PublicEndpoint[List[Header], Unit, List[Header], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(headers).out(headers)

  val in_json_out_headers: PublicEndpoint[FruitAmount, Unit, List[Header], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(jsonBody[FruitAmount]).out(headers)

  val in_paths_out_string: PublicEndpoint[List[String], Unit, String, Any] =
    endpoint.get.in(paths).out(stringBody)

  val in_path_paths_out_header_body: PublicEndpoint[(Int, List[String]), Unit, (Int, String), Any] =
    endpoint.get.in("api").in(path[Int]).in("and").in(paths).out(header[Int]("IntPath") and stringBody)

  val in_path_fixed_capture_fixed_capture: PublicEndpoint[(Int, Int), Unit, Unit, Any] =
    endpoint.get.in("customer" / path[Int]("customer_id") / "orders" / path[Int]("order_id"))

  val in_query_list_out_header_list: PublicEndpoint[List[String], Unit, List[String], Any] =
    endpoint.get.in("api" / "echo" / "param-to-header").in(query[List[String]]("qq")).out(header[List[String]]("hh"))

  val in_cookie_cookie_out_header: PublicEndpoint[(Int, String), Unit, List[String], Any] =
    endpoint.get
      .in("api" / "echo" / "headers")
      .in(cookie[Int]("c1"))
      .in(cookie[String]("c2"))
      .out(header[List[String]]("Set-Cookie"))

  val in_cookies_out_cookies: PublicEndpoint[List[Cookie], Unit, List[CookieWithMeta], Any] =
    endpoint.get.in("api" / "echo" / "headers").in(cookies).out(setCookies)

  val in_set_cookie_value_out_set_cookie_value: PublicEndpoint[CookieValueWithMeta, Unit, CookieValueWithMeta, Any] =
    endpoint.get.in("api" / "echo" / "headers").in(setCookie("c1")).out(setCookie("c1"))

  val in_query_out_cookie_raw: PublicEndpoint[String, Unit, (String, String), Any] =
    endpoint.get.in("").in(query[String]("q")).out(header[String]("Set-Cookie")).out(stringBody)

  val in_root_path: PublicEndpoint[Unit, Unit, Unit, Any] = endpoint.get.in("")

  val in_single_path: PublicEndpoint[Unit, Unit, Unit, Any] = endpoint.get.in("api")

  val in_extract_request_out_string: PublicEndpoint[String, Unit, String, Any] =
    endpoint.in(extractFromRequest(_.method.method)).out(stringBody)

  val in_string_out_status: PublicEndpoint[String, Unit, StatusCode, Any] =
    endpoint.in(query[String]("fruit")).out(statusCode)

  val delete_endpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.delete.in("api" / "delete").out(statusCode(StatusCode.Ok).description("ok"))

  val in_string_out_content_type_string: PublicEndpoint[String, Unit, (String, String), Any] =
    endpoint.in("api" / "echo").in(stringBody).out(stringBody).out(header[String]("Content-Type"))

  val in_content_type_out_string: PublicEndpoint[String, Unit, String, Any] =
    endpoint.in("api" / "echo").in(header[String]("Content-Type")).out(stringBody)

  val in_unit_out_html: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("api" / "echo").out(htmlBodyUtf8)

  val in_unit_out_header_redirect: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.out(statusCode(StatusCode.PermanentRedirect)).out(header[String]("Location"))

  val in_unit_out_fixed_header: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.out(header("Location", "Poland"))

  val in_optional_json_out_optional_json: PublicEndpoint[Option[FruitAmount], Unit, Option[FruitAmount], Any] =
    endpoint.post.in("api" / "echo").in(jsonBody[Option[FruitAmount]]).out(jsonBody[Option[FruitAmount]])

  val in_optional_coproduct_json_out_optional_coproduct_json: PublicEndpoint[Option[Entity], Unit, Option[Entity], Any] =
    endpoint.post.in("api" / "echo" / "coproduct").in(jsonBody[Option[Entity]]).out(jsonBody[Option[Entity]])

  val not_existing_endpoint: PublicEndpoint[Unit, String, Unit, Any] =
    endpoint.in("api" / "not-existing").errorOut(oneOf(oneOfVariant(StatusCode.BadRequest, stringBody)))

  val in_query_with_default_out_string: PublicEndpoint[String, Unit, String, Any] =
    endpoint
      .in(query[String]("p1").default("DEFAULT"))
      .out(stringBody)
      .name("Query with default")

  val out_fixed_content_type_header: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.out(stringBody.and(header("Content-Type", "text/csv")))

  val in_path_security_and_regular: Endpoint[Unit, Unit, Unit, String, Any] =
    endpoint.securityIn("auth").in("settings").out(stringBody)

  val in_path_security_no_regular: Endpoint[Unit, Unit, Unit, String, Any] =
    endpoint.securityIn("auth").out(stringBody)

  val out_custom_content_type_empty_body: PublicEndpoint[Int, Unit, String, Any] =
    endpoint.in(query[Int]("kind")).out(header[String](HeaderNames.ContentType))

  val out_custom_content_type_string_body: PublicEndpoint[Int, Unit, (String, String), Any] =
    endpoint.in(query[Int]("kind")).out(header[String](HeaderNames.ContentType)).out(stringBody)

  val in_raw_with_json_out_string: PublicEndpoint[(String, FruitAmount), Unit, String, Any] =
    endpoint.post
      .in("api" / "echo")
      .in(jsonBodyWithRaw[FruitAmount])
      .out(stringBody)

  val in_flag_query_out_string: PublicEndpoint[Option[Boolean], Unit, String, Any] =
    endpoint.get.in(query[Option[Boolean]]("flag").flagValue(Some(true))).out(stringBody)
}
