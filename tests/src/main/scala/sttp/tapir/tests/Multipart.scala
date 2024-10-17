package sttp.tapir.tests

import sttp.model.Part
import sttp.tapir._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.tests.data.{FruitAmount, FruitAmountWrapper, FruitData}

case class MultipleFileUpload(files: List[Part[TapirFile]])

object Multipart {
  val in_simple_multipart_out_multipart: PublicEndpoint[FruitAmount, Unit, FruitAmount, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(multipartBody[FruitAmount]).name("echo simple")

  val in_simple_multipart_out_string: PublicEndpoint[FruitAmount, Unit, String, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitAmount]).out(stringBody)

  val in_simple_multipart_out_raw_string: PublicEndpoint[FruitAmountWrapper, Unit, String, Any] = {
    endpoint.post.in("api" / "echo").in(multipartBody[FruitAmountWrapper]).out(stringBody)
  }

  val in_file_multipart_out_multipart: PublicEndpoint[FruitData, Unit, FruitData, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitData]).out(multipartBody[FruitData]).name("echo file")

  val in_file_multipart_out_string: PublicEndpoint[FruitData, Unit, String, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody[FruitData]).out(stringBody)

  val in_file_list_multipart_out_multipart: PublicEndpoint[MultipleFileUpload, Unit, MultipleFileUpload, Any] =
    endpoint.post
      .in("api" / "echo" / "multipart")
      .in(multipartBody[MultipleFileUpload])
      .out(multipartBody[MultipleFileUpload])
      .name("echo files")

  val in_raw_multipart_out_string: PublicEndpoint[Seq[Part[Array[Byte]]], Unit, String, Any] =
    endpoint.post.in("api" / "echo" / "multipart").in(multipartBody).out(stringBody).name("echo raw parts")
}
