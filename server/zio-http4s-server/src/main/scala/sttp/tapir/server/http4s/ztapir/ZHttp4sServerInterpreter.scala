package sttp.tapir.server.http4s.ztapir

import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.ztapir._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.{&, RIO, ZIO}

trait ZHttp4sServerInterpreter {
  def from[I, E, O, R](e: ZEndpoint[I, E, O])(logic: I => ZIO[R, E, O])(implicit
      serverOptions: Http4sServerOptions[RIO[R with Clock & Blocking, *], RIO[R with Clock & Blocking, *]]
  ): ServerEndpointsToRoutes[R] =
    from[R, I, E, O](e.zServerLogic(logic))

  def from[R, I, E, O](se: ZServerEndpoint[R, I, E, O])(implicit
      serverOptions: Http4sServerOptions[RIO[R with Clock & Blocking, *], RIO[R with Clock & Blocking, *]]
  ): ServerEndpointsToRoutes[R] = from[R](List(se))

  def from[R](
      serverEndpoints: List[ZServerEndpoint[R, _, _, _]]
  )(implicit
      serverOptions: Http4sServerOptions[RIO[R with Clock & Blocking, *], RIO[R with Clock & Blocking, *]]
  ): ServerEndpointsToRoutes[R] =
    new ServerEndpointsToRoutes[R](serverEndpoints)

  // This is needed to avoid too eager type inference. Having ZHttp4sServerInterpreter.toRoutes would require users
  // to explicitly provide the env type (R) as a type argument - so that it's not automatically inferred to include
  // Clock
  class ServerEndpointsToRoutes[R](
      serverEndpoints: List[ZServerEndpoint[R, _, _, _]]
  )(implicit serverOptions: Http4sServerOptions[RIO[R with Clock & Blocking, *], RIO[R with Clock & Blocking, *]]) {
    def toRoutes: HttpRoutes[RIO[R with Clock & Blocking, *]] = {
      Http4sServerInterpreter.toRoutes(serverEndpoints.map(_.widen[R with Clock & Blocking]))
    }
  }
}

object ZHttp4sServerInterpreter extends ZHttp4sServerInterpreter
