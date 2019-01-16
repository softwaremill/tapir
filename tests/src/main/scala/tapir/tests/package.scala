package tapir

import java.io.{File, InputStream}
import java.nio.ByteBuffer

import io.circe.generic.auto._
import tapir.json.circe._
import com.softwaremill.macwire._

package object tests {

  val in_query_out_string: Endpoint[String, Unit, String] = endpoint.in(query[String]("fruit")).out(stringBody)

  val in_query_query_out_string: Endpoint[(String, Option[Int]), Unit, String] =
    endpoint.in(query[String]("fruit")).in(query[Option[Int]]("amount")).out(stringBody)

  val in_header_out_string: Endpoint[String, Unit, String] = endpoint.in(header[String]("X-Role")).out(stringBody)

  val in_path_path_out_string: Endpoint[(String, Int), Unit, String] =
    endpoint.in("fruit" / path[String] / "amount" / path[Int]).out(stringBody)

  val in_string_out_string: Endpoint[String, Unit, String] = endpoint.post.in("api" / "echo").in(stringBody).out(stringBody)

  val in_mapped_query_out_string: Endpoint[List[Char], Unit, String] =
    endpoint.in(query[String]("fruit").map(_.toList)(_.mkString(""))).out(stringBody)

  val in_mapped_path_out_string: Endpoint[Fruit, Unit, String] =
    endpoint.in(("fruit" / path[String]).mapTo(Fruit)).out(stringBody)

  val in_mapped_path_path_out_string: Endpoint[FruitAmount, Unit, String] =
    endpoint.in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount)).out(stringBody)

  val in_query_mapped_path_path_out_string: Endpoint[(FruitAmount, String), Unit, String] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(stringBody)

  val in_query_out_mapped_string: Endpoint[String, Unit, List[Char]] =
    endpoint.in(query[String]("fruit")).out(stringBody.map(_.toList)(_.mkString("")))

  val in_query_out_mapped_string_header: Endpoint[String, Unit, FruitAmount] = endpoint
    .in(query[String]("fruit"))
    .out(stringBody.and(header[Int]("X-Role")).mapTo(FruitAmount))

  val in_json_out_json: Endpoint[FruitAmount, Unit, FruitAmount] =
    endpoint.post.in("api" / "echo").in(jsonBody[FruitAmount]).out(jsonBody[FruitAmount]).name("echo json")

  val in_byte_array_out_byte_array: Endpoint[Array[Byte], Unit, Array[Byte]] =
    endpoint.post.in("api" / "echo").in(binaryBody[Array[Byte]]).out(binaryBody[Array[Byte]]).name("echo byte array")

  val in_byte_buffer_out_byte_buffer: Endpoint[ByteBuffer, Unit, ByteBuffer] =
    endpoint.post.in("api" / "echo").in(binaryBody[ByteBuffer]).out(binaryBody[ByteBuffer]).name("echo byte buffer")

  val in_input_stream_out_input_stream: Endpoint[InputStream, Unit, InputStream] =
    endpoint.post.in("api" / "echo").in(binaryBody[InputStream]).out(binaryBody[InputStream]).name("echo input stream")

  val in_file_out_file: Endpoint[File, Unit, File] =
    endpoint.post.in("api" / "echo").in(binaryBody[File]).out(binaryBody[File]).name("echo file")

  val in_unit_out_string: Endpoint[Unit, Unit, String] =
    endpoint.in("api").out(stringBody)

  val in_unit_error_out_string: Endpoint[Unit, String, Unit] =
    endpoint.in("api").errorOut(stringBody)

  val in_form_out_form: Endpoint[FruitAmount, Unit, FruitAmount] =
    endpoint.post.in("api" / "echo").in(formBody[FruitAmount]).out(formBody[FruitAmount])

  val in_query_params_out_string: Endpoint[MultiQueryParams, Unit, String] =
    endpoint.get.in("api" / "echo" / "params").in(queryParams).out(stringBody)

  val allTestEndpoints: Set[Endpoint[_, _, _]] = wireSet[Endpoint[_, _, _]]
}
