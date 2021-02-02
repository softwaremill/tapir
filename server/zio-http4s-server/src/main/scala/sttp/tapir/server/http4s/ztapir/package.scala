package sttp.tapir.server.http4s

import org.http4s.HttpRoutes
import sttp.tapir.ztapir._
import zio.clock.Clock
import zio.{RIO, ZIO}

package object ztapir {
  implicit class RichZEndpointRoutes[I, E, O](e: ZEndpoint[I, E, O]) {
    @deprecated("Use ZHttp4sServerInterpreter.from.toRoutes", since = "0.17.1")
    def toRoutes[R](logic: I => ZIO[R, E, O])(implicit
        serverOptions: Http4sServerOptions[RIO[R with Clock, *]]
    ): HttpRoutes[RIO[R with Clock, *]] =
      ZHttp4sServerInterpreter.from[R, I, E, O](e.zServerLogic(logic)).toRoutes
  }

  implicit class RichZServerEndpointRoutes[R, I, E, O](se: ZServerEndpoint[R, I, E, O]) {
    @deprecated("Use ZHttp4sServerInterpreter.from.toRoutes", since = "0.17.1")
    def toRoutes(implicit serverOptions: Http4sServerOptions[RIO[R with Clock, *]]): HttpRoutes[RIO[R with Clock, *]] =
      ZHttp4sServerInterpreter.from[R](List(se)).toRoutes
  }

  implicit class RichZServerEndpointsRoutes[R, I, E, O](serverEndpoints: List[ZServerEndpoint[R, _, _, _]]) {
    @deprecated("Use ZHttp4sServerInterpreter.from.toRoutes", since = "0.17.1")
    def toRoutes(implicit serverOptions: Http4sServerOptions[RIO[R with Clock, *]]): HttpRoutes[RIO[R with Clock, *]] = {
      ZHttp4sServerInterpreter.from(serverEndpoints).toRoutes
    }
  }
}
