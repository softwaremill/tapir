package sttp.tapir.server.interceptor

import sttp.model.{Header, StatusCode}
import sttp.tapir.model.ServerResponse
import scala.collection.immutable.Seq

case class ServerResponseFromOutput[B](code: StatusCode, headers: Seq[Header], body: Option[B], source: ValuedEndpointOutput[_])
    extends ServerResponse[B]
