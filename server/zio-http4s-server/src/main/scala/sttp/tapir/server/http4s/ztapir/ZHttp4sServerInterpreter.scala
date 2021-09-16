package sttp.tapir.server.http4s.ztapir

import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.ztapir._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.{RIO, ZIO}

trait ZHttp4sServerInterpreter[R] {

  def zHttp4sServerOptions: Http4sServerOptions[RIO[R with Clock with Blocking, *], RIO[R with Clock with Blocking, *]] =
    Http4sServerOptions.default

  def from[I, E, O](e: ZEndpoint[I, E, O])(logic: I => ZIO[R, E, O]): ServerEndpointsToRoutes =
    from[I, E, O](e.zServerLogic(logic))

  def from[I, E, O](se: ZServerEndpoint[R, I, E, O]): ServerEndpointsToRoutes = from(List(se))

  def from(serverEndpoints: List[ZServerEndpoint[R, _, _, _]]): ServerEndpointsToRoutes =
    new ServerEndpointsToRoutes(serverEndpoints)

  // This is needed to avoid too eager type inference. Having ZHttp4sServerInterpreter.toRoutes would require users
  // to explicitly provide the env type (R) as a type argument - so that it's not automatically inferred to include
  // Clock
  class ServerEndpointsToRoutes(
      serverEndpoints: List[ZServerEndpoint[R, _, _, _]]
  ) {
    def toRoutes: HttpRoutes[RIO[R with Clock with Blocking, *]] = {
      Http4sServerInterpreter(zHttp4sServerOptions).toRoutes(
        serverEndpoints.map(se => ConvertStreams(se.widen[R with Clock with Blocking]))
      )
    }
  }
}

object ZHttp4sServerInterpreter {
  def apply[R](): ZHttp4sServerInterpreter[R] = {
    new ZHttp4sServerInterpreter[R] {}
  }

  def apply[R](
      serverOptions: Http4sServerOptions[RIO[R with Clock with Blocking, *], RIO[R with Clock with Blocking, *]]
  ): ZHttp4sServerInterpreter[R] = {
    new ZHttp4sServerInterpreter[R] {
      override def zHttp4sServerOptions: Http4sServerOptions[RIO[R with Clock with Blocking, *], RIO[R with Clock with Blocking, *]] =
        serverOptions
    }
  }
}
