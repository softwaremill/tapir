package sttp.tapir.server.interceptor

import sttp.model.{Header, StatusCode}
import sttp.tapir.model.ServerResponse
import scala.collection.immutable.Seq

/** @param source
  *   The output, from which this response has been created. Might be [[ValuedEndpointOutput.Empty]] if no output is available.
  */
case class ServerResponseFromOutput[B](code: StatusCode, headers: Seq[Header], body: Option[B], source: ValuedEndpointOutput[_])
    extends ServerResponse[B]
