package tapir

import java.io.{File, InputStream, PrintWriter}
import java.nio.ByteBuffer

import io.circe.generic.auto._
import tapir.json.circe._
import com.softwaremill.macwire._
import tapir.model._

import scala.io.Source

package object tests {

  val in_query_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.in(query[String]("fruit")).out(stringBody)

  val in_query_query_out_string: Endpoint[(String, Option[Int]), Unit, String, Nothing] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(stringBody)

  val in_header_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.in(header[String]("X-Role")).out(stringBody)

  val in_path_path_out_string: Endpoint[(String, Int), Unit, String, Nothing] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(stringBody)

  val in_string_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.post.in("api" / "echo").in(stringBody).out(stringBody)

  val in_mapped_query_out_string: Endpoint[List[Char], Unit, String, Nothing] =
    endpoint.in(query[String]("fruit").map(_.toList)(_.mkString(""))).out(stringBody)

  val in_mapped_path_out_string: Endpoint[Fruit, Unit, String, Nothing] =
    endpoint.in(("fruit" / path[String]).mapTo(Fruit)).out(stringBody)

  val in_mapped_path_path_out_string: Endpoint[FruitAmount, Unit, String, Nothing] =
    endpoint.in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount)).out(stringBody)

  val in_query_mapped_path_path_out_string: Endpoint[(FruitAmount, String), Unit, String, Nothing] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(stringBody)

  val in_query_out_mapped_string: Endpoint[String, Unit, List[Char], Nothing] =
    endpoint.in(query[String]("fruit")).out(stringBody.map(_.toList)(_.mkString("")))

  val in_query_out_mapped_string_header: Endpoint[String, Unit, FruitAmount, Nothing] = endpoint
    .in(query[String]("fruit"))
    .out(stringBody.and(header[Int]("X-Role")).mapTo(FruitAmount))

  val in_json_out_json: Endpoint[FruitAmount, Unit, FruitAmount, Nothing] =
    endpoint.post.in("api" / "echo").in(jsonBody[FruitAmount]).out(jsonBody[FruitAmount]).name("echo json")

  val in_byte_array_out_byte_array: Endpoint[Array[Byte], Unit, Array[Byte], Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[Array[Byte]]).out(binaryBody[Array[Byte]]).name("echo byte array")

  val in_byte_buffer_out_byte_buffer: Endpoint[ByteBuffer, Unit, ByteBuffer, Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[ByteBuffer]).out(binaryBody[ByteBuffer]).name("echo byte buffer")

  val in_input_stream_out_input_stream: Endpoint[InputStream, Unit, InputStream, Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[InputStream]).out(binaryBody[InputStream]).name("echo input stream")

  val in_file_out_file: Endpoint[File, Unit, File, Nothing] =
    endpoint.post.in("api" / "echo").in(binaryBody[File]).out(binaryBody[File]).name("echo file")

  val in_unit_out_string: Endpoint[Unit, Unit, String, Nothing] =
    endpoint.in("api").out(stringBody)

  val in_unit_error_out_string: Endpoint[Unit, String, Unit, Nothing] =
    endpoint.in("api").errorOut(stringBody)

  val in_form_out_form: Endpoint[FruitAmount, Unit, FruitAmount, Nothing] =
    endpoint.post.in("api" / "echo").in(formBody[FruitAmount]).out(formBody[FruitAmount])

  val in_query_params_out_string: Endpoint[MultiQueryParams, Unit, String, Nothing] =
    endpoint.get.in("api" / "echo" / "params").in(queryParams).out(stringBody)

  val in_headers_out_headers: Endpoint[Seq[(String, String)], Unit, Seq[(String, String)], Nothing] =
    endpoint.get.in("api" / "echo" / "headers").in(headers).out(headers)

  val in_paths_out_string: Endpoint[Seq[String], Unit, String, Nothing] =
    endpoint.get.in(paths).out(stringBody)

  val in_query_list_out_header_list: Endpoint[List[String], Unit, List[String], Nothing] =
    endpoint.get.in("api" / "echo" / "param-to-header").in(query[List[String]]("qq")).out(header[List[String]]("hh"))

  def in_stream_out_stream[S]: Endpoint[S, Unit, S, S] = {
    val sb = streamBody[S](schemaFor[String], MediaType.TextPlain())
    endpoint.post.in("api" / "echo").in(sb).out(sb)
  }

  val in_simple_multipart_out_multipart: Endpoint[FruitAmount, Unit, FruitAmount, Nothing] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(multipartBody[FruitAmount]).name("echo simple")

  val in_simple_multipart_out_string: Endpoint[FruitAmount, Unit, String, Nothing] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(stringBody)

  val in_file_multipart_out_multipart: Endpoint[FruitData, Unit, FruitData, Nothing] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitData]).out(multipartBody[FruitData]).name("echo file")

  val in_cookie_cookie_out_header: Endpoint[(Int, String), Unit, List[String], Nothing] =
    endpoint.get
      .in("api" / "echo" / "headers")
      .in(cookie[Int]("c1"))
      .in(cookie[String]("c2"))
      .out(header[List[String]]("Cookie"))

  val in_cookies_out_cookies: Endpoint[List[Cookie], Unit, List[SetCookie], Nothing] =
    endpoint.get.in("api" / "echo" / "headers").in(cookies).out(setCookies)

  val in_set_cookie_value_out_set_cookie_value: Endpoint[SetCookieValue, Unit, SetCookieValue, Nothing] =
    endpoint.get.in("api" / "echo" / "headers").in(setCookie("c1")).out(setCookie("c1"))

  val in_root_path: Endpoint[Unit, Unit, Unit, Nothing] = endpoint.get.in("")

  val in_single_path: Endpoint[Unit, Unit, Unit, Nothing] = endpoint.get.in("api")

  val in_extract_request_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.in(extractFromRequest(_.method.m)).out(stringBody)

  val in_auth_apikey_header_out_string: Endpoint[String, Unit, String, Nothing] =
    endpoint.in("auth").in(auth.apiKey(header[String]("X-Api-Key"))).out(stringBody)

  val in_auth_apikey_query_out_string: Endpoint[String, Unit, String, Nothing] =
    endpoint.in("auth").in(auth.apiKey(query[String]("api-key"))).out(stringBody)

  val in_auth_basic_out_string: Endpoint[UsernamePassword, Unit, String, Nothing] = endpoint.in("auth").in(auth.basic).out(stringBody)

  val in_auth_bearer_out_string: Endpoint[String, Unit, String, Nothing] = endpoint.in("auth").in(auth.bearer).out(stringBody)

  val in_string_out_status_from_string: Endpoint[String, Unit, String, Nothing] =
    endpoint.in(query[String]("fruit")).out(statusFrom(stringBody, StatusCodes.Ok, whenValue[String](_ == "x") -> StatusCodes.Accepted))

  val in_string_out_status: Endpoint[String, Unit, StatusCode, Nothing] =
    endpoint.in(query[String]("fruit")).out(statusCode)

  val allTestEndpoints: Set[Endpoint[_, _, _, _]] = wireSet[Endpoint[_, _, _, _]]

  def writeToFile(s: String): File = {
    val f = File.createTempFile("test", "tapir")
    new PrintWriter(f) { write(s); close() }
    f.deleteOnExit()
    f
  }

  def readFromFile(f: File): String = Source.fromFile(f).mkString
}
