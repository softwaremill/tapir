package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zio._
import zio.interop.catz._

class ZioHttpTestServerInterpreter(nettyDeps: EventLoopGroup with ServerChannelFactory)
    extends TestServerInterpreter[Task, ZioStreams, ZioHttpServerOptions[Any], Http[Any, Throwable, Request, Response]] {

  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): Http[Any, Throwable, Request, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = interceptors(ZioHttpServerOptions.customiseInterceptors).options
    ZioHttpInterpreter(serverOptions).toHttp(es)
  }

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default
    val server: Server[Any, Throwable] = Server.app(routes.toList.reduce(_ ++ _)) ++ Server.maxRequestSize(10000000)
    Server
      .make(server ++ Server.port(0))
      .provide(nettyDeps)
      .map(_.port)
      .toResource[IO]
  }
}
