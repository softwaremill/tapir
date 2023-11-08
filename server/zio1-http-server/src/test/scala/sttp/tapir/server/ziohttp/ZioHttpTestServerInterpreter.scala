package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zio._
import zio.interop.catz._
import scala.concurrent.duration.FiniteDuration

class ZioHttpTestServerInterpreter(nettyDeps: EventLoopGroup with ServerChannelFactory)
    extends TestServerInterpreter[Task, ZioStreams, ZioHttpServerOptions[Any], Http[Any, Throwable, Request, Response]] {

  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): Http[Any, Throwable, Request, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = interceptors(ZioHttpServerOptions.customiseInterceptors).options
    ZioHttpInterpreter(serverOptions).toHttp(es)
  }

  override def serverWithStop(
      routes: NonEmptyList[Http[Any, Throwable, Request, Response]],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, (Port, KillSwitch)] = {
    implicit val r: Runtime[Any] = Runtime.default
    val server: Server[Any, Throwable] = Server.app(routes.toList.reduce(_ ++ _))
    // ZIO HTTP 1.x doesn't offer graceful shutdown with timeout OOTB
    Resource.make(
      Server
        .make(server ++ Server.port(0))
        .provide(nettyDeps)
        .map(_.port)
        .toResource[IO]
        .allocated
    ) { case (_, release) => release }
  }
}
