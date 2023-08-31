package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.{ChannelFactory, EventLoopGroup, ServerChannel}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio._
import zio.http._
import zio.interop.catz._

class ZioHttpTestServerInterpreter(
    eventLoopGroup: ZLayer[Any, Nothing, EventLoopGroup],
    channelFactory: ZLayer[Any, Nothing, ChannelFactory[ServerChannel]]
)(implicit
    trace: Trace
) extends TestServerInterpreter[Task, ZioStreams with WebSockets, ZioHttpServerOptions[Any], Http[Any, Throwable, Request, Response]] {

  override def route(es: List[ServerEndpoint[ZioStreams with WebSockets, Task]], interceptors: Interceptors): Http[Any, Throwable, Request, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = interceptors(ZioHttpServerOptions.customiseInterceptors).options
    ZioHttpInterpreter(serverOptions).toHttp(es)
  }

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default

    val effect: ZIO[Scope, Throwable, Int] =
      (for {
        driver <- ZIO.service[Driver]
        result <- driver.start(trace)
        _ <- driver.addApp[Any](routes.toList.reduce(_ ++ _).withDefaultErrorResponse, ZEnvironment())
      } yield result.port)
        .provideSome[Scope](
          zio.test.driver,
          eventLoopGroup,
          channelFactory,
          ZLayer.succeed(Server.Config.default.port(0).enableRequestStreaming)
        )

    Resource.scoped[IO, Any, Int](effect)
  }
}
