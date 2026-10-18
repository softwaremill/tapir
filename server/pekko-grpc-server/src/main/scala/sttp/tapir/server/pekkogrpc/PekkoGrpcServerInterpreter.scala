package sttp.tapir.server.pekkogrpc

import org.apache.pekko.http.scaladsl.server.Route
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.{ExecutionContext, Future}

trait PekkoGrpcServerInterpreter extends PekkoHttpServerInterpreter {
  override def toRoute(ses: List[ServerEndpoint[PekkoStreams & WebSockets, Future]]): Route =
    toRoute(new PekkoGrpcRequestBody(pekkoHttpServerOptions)(using _, _), new PekkoGrpcToResponseBody()(using _, _))(ses)

}

object PekkoGrpcServerInterpreter {
  def apply()(implicit _ec: ExecutionContext): PekkoGrpcServerInterpreter = new PekkoGrpcServerInterpreter {
    override implicit def executionContext: ExecutionContext = _ec
  }
}
