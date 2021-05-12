package sttp.tapir.serverless.aws.lambda

import cats.effect.IO
import cats.implicits._
import com.softwaremill.macwire.wireSet
import sttp.model.Header
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests._

package object tests {
  val empty_endpoint: ServerEndpoint[Unit, Unit, Unit, Any, IO] = endpoint.serverLogic(_ => IO.pure(().asRight[Unit]))

  val empty_get_endpoint: ServerEndpoint[Unit, Unit, Unit, Any, IO] = endpoint.get.serverLogic(_ => IO.pure(().asRight[Unit]))

  val in_path_path_out_string_endpoint: ServerEndpoint[(String, Port), Unit, String, Any, IO] = in_path_path_out_string.serverLogic {
    case (fruit: String, amount: Int) => IO.pure(s"$fruit $amount".asRight[Unit])
  }

  val in_string_out_string_endpoint: ServerEndpoint[String, Unit, String, Any, IO] =
    in_string_out_string.in("string").serverLogic(s => IO.pure(s.asRight[Unit]))

  val in_json_out_json_endpoint: ServerEndpoint[FruitAmount, Unit, FruitAmount, Any, IO] =
    in_json_out_json.in("json").serverLogic(fa => IO.pure(fa.asRight[Unit]))

  val in_headers_out_headers_endpoint: ServerEndpoint[List[Header], Unit, List[Header], Any, IO] = in_headers_out_headers.serverLogic {
    headers => IO.pure(headers.asRight[Unit])
  }

  val allEndpoints: Set[ServerEndpoint[_, _, _, Any, IO]] = wireSet[ServerEndpoint[_, _, _, Any, IO]]
}
