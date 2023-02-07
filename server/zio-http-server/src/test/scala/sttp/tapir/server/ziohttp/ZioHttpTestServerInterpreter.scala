package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio._
import zio.http.netty.server.NettyDriver
import zio.http._
import zio.interop.catz._

class ZioHttpTestServerInterpreter(
    eventLoopGroup: ZLayer[Any, Nothing, zio.http.service.EventLoopGroup],
    channelFactory: ZLayer[Any, Nothing, zio.http.service.ServerChannelFactory]
)(implicit
    trace: Trace
) extends TestServerInterpreter[Task, ZioStreams, ZioHttpServerOptions[Any], Http[Any, Throwable, Request, Response]] {

  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): Http[Any, Throwable, Request, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = interceptors(ZioHttpServerOptions.customiseInterceptors).options
    ZioHttpInterpreter(serverOptions).toHttp(es)
  }

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default

    val effect: ZIO[Scope, Throwable, Int] =
      (for {
        driver <- ZIO.service[Driver]
        port <- driver.start(trace)
        _ <- driver.addApp[Any](routes.toList.reduce(_ ++ _).withDefaultErrorResponse, ZEnvironment())
      } yield port)
        .provideSome[Scope](
          NettyDriver.manual,
          eventLoopGroup,
          channelFactory,
          ServerConfig.live(ServerConfig.default.port(0).objectAggregator(1000000))
        )

    Resource.scoped[IO, Any, Int](effect)
  }
}
