package sttp.tapir.server.akkagrpc

import akka.http.scaladsl.server.Route
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.{ExecutionContext, Future}

trait AkkaGrpcServerInterpreter extends AkkaHttpServerInterpreter {
  override def toRoute(ses: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]): Route =
    toRoute(new AkkaGrpcRequestBody(akkaHttpServerOptions)(_, _), new AkkaGrpcToResponseBody()(_, _))(ses)

}

object AkkaGrpcServerInterpreter {
  def apply(ec: ExecutionContext): AkkaGrpcServerInterpreter = new AkkaGrpcServerInterpreter {
    override implicit def executionContext: ExecutionContext = ec
  }
}
