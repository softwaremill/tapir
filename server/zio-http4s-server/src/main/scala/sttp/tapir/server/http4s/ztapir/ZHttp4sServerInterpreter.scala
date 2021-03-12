package sttp.tapir.server.http4s.ztapir

import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.ztapir._
import zio.{RIO, ZIO}
import zio.clock.Clock
import zio.interop.catz._

trait ZHttp4sServerInterpreter {
  def from[I, E, O, R](e: ZEndpoint[I, E, O])(logic: I => ZIO[R, E, O])(implicit
      serverOptions: Http4sServerOptions[RIO[R with Clock, *], RIO[R with Clock, *]]
  ): ServerEndpointsToRoutes[R] =
    from[R, I, E, O](e.zServerLogic(logic))

  def from[R, I, E, O](se: ZServerEndpoint[R, I, E, O])(implicit
      serverOptions: Http4sServerOptions[RIO[R with Clock, *], RIO[R with Clock, *]]
  ): ServerEndpointsToRoutes[R] = from[R](List(se))

  def from[R](
      serverEndpoints: List[ZServerEndpoint[R, _, _, _]]
  )(implicit serverOptions: Http4sServerOptions[RIO[R with Clock, *], RIO[R with Clock, *]]): ServerEndpointsToRoutes[R] =
    new ServerEndpointsToRoutes[R](serverEndpoints, serverOptions)

  // This is needed to avoid too eager type inference. Having ZHttp4sServerInterpreter.toRoutes would require users
  // to explicitly provide the env type (R) as a type argument - so that it's not automatically inferred to include
  // Clock
  class ServerEndpointsToRoutes[R](
      serverEndpoints: List[ZServerEndpoint[R, _, _, _]],
      serverOptions: Http4sServerOptions[RIO[R with Clock, *], RIO[R with Clock, *]]
  ) {
    def toRoutes: HttpRoutes[RIO[R with Clock, *]] = {
      Http4sServerInterpreter.toRoutes(serverEndpoints.map(_.widen[R with Clock]))
    }
  }
}

object ZHttp4sServerInterpreter extends ZHttp4sServerInterpreter
