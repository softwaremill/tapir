package sttp.tapir.serverless.aws.lambda.zio

import sttp.tapir._
import sttp.tapir.tests.Basic._
import sttp.tapir.tests.Mapping.in_4query_out_4header_extended
import sttp.tapir.tests.TestUtil.inputStreamToByteArray
import sttp.tapir.ztapir.ZTapir
import zio.ZIO

import java.io.{ByteArrayInputStream, InputStream}

package object tests extends ZTapir {

  type ZioEndpoint = ZServerEndpoint[Any, Any]

  // this endpoint is used to wait until sam local starts up before running actual tests
  val health_endpoint: ZioEndpoint =
    endpoint.get.in("health").zServerLogic(_ => ZIO.unit)

  val in_path_path_out_string_endpoint: ZioEndpoint =
    in_path_path_out_string.zServerLogic { case (fruit: String, amount: Int) =>
      ZIO.attempt(s"$fruit $amount").mapError(_ => ())
    }

  val in_string_out_string_endpoint: ZioEndpoint =
    in_string_out_string.in("string").zServerLogic(v => ZIO.attempt(v).mapError(_ => ()))

  val in_json_out_json_endpoint: ZioEndpoint =
    in_json_out_json.in("json").zServerLogic(v => ZIO.attempt(v).mapError(_ => ()))

  val in_headers_out_headers_endpoint: ZioEndpoint =
    in_headers_out_headers.zServerLogic(v => ZIO.attempt(v).mapError(_ => ()))

  val in_input_stream_out_input_stream_endpoint: ZioEndpoint =
    in_input_stream_out_input_stream.in("is").zServerLogic { is =>
      ZIO.attempt(new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).orDie
    }

  val in_4query_out_4header_extended_endpoint: ZioEndpoint =
    in_4query_out_4header_extended.in("echo" / "query").zServerLogic(v => ZIO.attempt(v).mapError(_ => ()))

  val allEndpoints: Set[ZioEndpoint] = Set(
    health_endpoint,
    in_path_path_out_string_endpoint,
    in_string_out_string_endpoint,
    in_json_out_json_endpoint,
    in_headers_out_headers_endpoint,
    in_input_stream_out_input_stream_endpoint,
    in_4query_out_4header_extended_endpoint
  )

}
