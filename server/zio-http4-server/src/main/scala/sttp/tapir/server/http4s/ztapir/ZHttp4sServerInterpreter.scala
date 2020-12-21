package sttp.tapir.server.http4s.ztapir

import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{EndpointToHttp4sServer, Http4sServerOptions}
import sttp.tapir.ztapir._
import zio.{RIO, ZIO}
import zio.clock.Clock
import zio.interop.catz._

trait ZHttp4sServerInterpreter {
  def toRoutes[I, E, O, R](e: ZEndpoint[I, E, O])(logic: I => ZIO[R, E, O])(implicit
      serverOptions: Http4sServerOptions[RIO[R with Clock, *]]
  ): HttpRoutes[RIO[R with Clock, *]] =
    toRoutes[R, I, E, O](e.zServerLogic(logic))

  def toRoutes[R, I, E, O](se: ZServerEndpoint[R, I, E, O])(implicit
      serverOptions: Http4sServerOptions[RIO[R with Clock, *]]
  ): HttpRoutes[RIO[R with Clock, *]] = toRoutes[R](List(se))

  def toRoutes[R](
      serverEndpoints: List[ZServerEndpoint[R, _, _, _]]
  )(implicit serverOptions: Http4sServerOptions[RIO[R with Clock, *]]): HttpRoutes[RIO[R with Clock, *]] = {
    new EndpointToHttp4sServer(serverOptions).toRoutes(serverEndpoints.map(_.widen[R with Clock]))
  }
}

object ZHttp4sServerInterpreter extends ZHttp4sServerInterpreter
