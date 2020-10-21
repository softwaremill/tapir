package sttp.tapir.server.http4s

import org.http4s.HttpRoutes
import sttp.tapir.ztapir._
import zio.interop.catz._
import zio.{RIO, ZIO}

package object ztapir {
  implicit class RichZEndpointRoutes[I, E, O](e: ZEndpoint[I, E, O]) {
    def toRoutes[R](logic: I => ZIO[R, E, O])(implicit serverOptions: Http4sServerOptions[RIO[R, *]]): HttpRoutes[RIO[R, *]] =
      e.zServerLogic(logic).toRoutes
  }

  implicit class RichZServerEndpointRoutes[R, I, E, O](se: ZServerEndpoint[R, I, E, O]) {
    def toRoutes(implicit serverOptions: Http4sServerOptions[RIO[R, *]]): HttpRoutes[RIO[R, *]] = List(se).toRoutes
  }

  implicit class RichZServerEndpointsRoutes[R, I, E, O](serverEndpoints: List[ZServerEndpoint[R, _, _, _]]) {
    def toRoutes(implicit serverOptions: Http4sServerOptions[RIO[R, *]]): HttpRoutes[RIO[R, *]] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(serverEndpoints)
    }
  }
}
