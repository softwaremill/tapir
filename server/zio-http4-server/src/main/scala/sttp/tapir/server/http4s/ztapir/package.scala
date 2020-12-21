package sttp.tapir.server.http4s

import org.http4s.HttpRoutes
import sttp.tapir.ztapir._
import zio.clock.Clock
import zio.interop.catz._
import zio.{RIO, ZIO}

package object ztapir {
  implicit class RichZEndpointRoutes[I, E, O](e: ZEndpoint[I, E, O]) {
    @deprecated("Use ZHttp4sServerInterpreter.toRoutes", since = "0.17.1")
    def toRoutes[R](logic: I => ZIO[R, E, O])(implicit
        serverOptions: Http4sServerOptions[RIO[R with Clock, *]]
    ): HttpRoutes[RIO[R with Clock, *]] =
      ZHttp4sServerInterpreter.toRoutes[R, I, E, O](e.zServerLogic(logic))
  }

  implicit class RichZServerEndpointRoutes[R, I, E, O](se: ZServerEndpoint[R, I, E, O]) {
    @deprecated("Use ZHttp4sServerInterpreter.toRoutes", since = "0.17.1")
    def toRoutes(implicit serverOptions: Http4sServerOptions[RIO[R with Clock, *]]): HttpRoutes[RIO[R with Clock, *]] =
      ZHttp4sServerInterpreter.toRoutes[R](List(se))
  }

  implicit class RichZServerEndpointsRoutes[R, I, E, O](serverEndpoints: List[ZServerEndpoint[R, _, _, _]]) {
    @deprecated("Use ZHttp4sServerInterpreter.toRoutes", since = "0.17.1")
    def toRoutes(implicit serverOptions: Http4sServerOptions[RIO[R with Clock, *]]): HttpRoutes[RIO[R with Clock, *]] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(serverEndpoints.map(_.widen[R with Clock]))
    }
  }
}
