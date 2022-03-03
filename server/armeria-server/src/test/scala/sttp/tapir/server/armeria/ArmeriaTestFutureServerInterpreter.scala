package sttp.tapir.server.armeria

import sttp.capabilities.armeria.ArmeriaStreams
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

class ArmeriaTestFutureServerInterpreter extends ArmeriaTestServerInterpreter[ArmeriaStreams, Future, ArmeriaFutureServerOptions] {

  override def route(es: List[ServerEndpoint[ArmeriaStreams, Future]], interceptors: Interceptors): TapirService[ArmeriaStreams, Future] = {
    val serverOptions: ArmeriaFutureServerOptions = interceptors(ArmeriaFutureServerOptions.customInterceptors).options
    ArmeriaFutureServerInterpreter(serverOptions).toService(es)
  }
}
