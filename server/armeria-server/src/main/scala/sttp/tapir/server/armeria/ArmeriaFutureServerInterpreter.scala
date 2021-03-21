package sttp.tapir.server.armeria

import scala.concurrent.Future
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.tapir.server.ServerEndpoint

trait ArmeriaFutureServerInterpreter {

  def armeriaServerOptions: ArmeriaFutureServerOptions = ArmeriaFutureServerOptions.default

  def toRoute(serverEndpoint: ServerEndpoint[ArmeriaStreams, Future]): TapirService[ArmeriaStreams, Future] =
    toRoute(List(serverEndpoint))

  def toRoute(serverEndpoints: List[ServerEndpoint[ArmeriaStreams, Future]]): TapirService[ArmeriaStreams, Future] =
    TapirFutureService(serverEndpoints, armeriaServerOptions)
}

object ArmeriaFutureServerInterpreter extends ArmeriaFutureServerInterpreter {
  def apply(serverOptions: ArmeriaFutureServerOptions = ArmeriaFutureServerOptions.default): ArmeriaFutureServerInterpreter = {
    new ArmeriaFutureServerInterpreter {
      override def armeriaServerOptions: ArmeriaFutureServerOptions = serverOptions
    }
  }
}
