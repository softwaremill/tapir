package sttp.tapir.serverless.aws.cdk

import cats.effect.IO
import cats.implicits._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests.Basic._
import sttp.tapir.tests.Mapping.in_4query_out_4header_extended
import sttp.tapir.tests.TestUtil.inputStreamToByteArray

import java.io.{ByteArrayInputStream, InputStream}

package object tests {

  // this endpoint is used to wait until sam local starts up before running actual tests
  val health_endpoint: ServerEndpoint[Any, IO] =
    endpoint.get.in("health").serverLogic(_ => IO.pure(().asRight[Unit]))

  val in_path_path_out_string_endpoint: ServerEndpoint[Any, IO] =
    in_path_path_out_string.get.serverLogic { case (fruit: String, amount: Int) =>
      IO.pure(s"$fruit $amount".asRight[Unit])
    }

  val in_string_out_string_endpoint: ServerEndpoint[Any, IO] =
    in_string_out_string.in("string").serverLogic(s => IO.pure(s.asRight[Unit]))

  val in_json_out_json_endpoint: ServerEndpoint[Any, IO] =
    in_json_out_json.in("json").serverLogic(fa => IO.pure(fa.asRight[Unit]))

  val in_headers_out_headers_endpoint: ServerEndpoint[Any, IO] =
    in_headers_out_headers.serverLogic { headers =>
      IO.pure(headers.asRight[Unit])
    }

  val in_input_stream_out_input_stream_endpoint: ServerEndpoint[Any, IO] =
    in_input_stream_out_input_stream.in("is").serverLogic { is =>
      IO.pure((new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).asRight[Unit])
    }

  val in_4query_out_4header_extended_endpoint: ServerEndpoint[Any, IO] =
    in_4query_out_4header_extended.get.in("echo" / "query").serverLogic { in => IO.pure(in.asRight[Unit]) }

  val allEndpoints: Set[ServerEndpoint[Any, IO]] = Set(
    health_endpoint,
    in_path_path_out_string_endpoint,
    in_string_out_string_endpoint,
    in_json_out_json_endpoint,
    in_headers_out_headers_endpoint,
    in_input_stream_out_input_stream_endpoint,
    in_4query_out_4header_extended_endpoint
  )
}
