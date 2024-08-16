package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.{ChannelFactory, EventLoopGroup, ServerChannel}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._
import zio._
import zio.http._
import zio.interop.catz._
import scala.concurrent.duration.FiniteDuration

class ZioHttpTestServerInterpreter(
    eventLoopGroup: ZLayer[Any, Nothing, EventLoopGroup],
    channelFactory: ZLayer[Any, Nothing, ChannelFactory[ServerChannel]]
) extends TestServerInterpreter[Task, ZioStreams with WebSockets, ZioHttpServerOptions[Any], Routes[Any, Response]] {

  override def route(
      es: List[ServerEndpoint[ZioStreams with WebSockets, Task]],
      interceptors: Interceptors
  ): Routes[Any, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = interceptors(ZioHttpServerOptions.customiseInterceptors).options
    ZioHttpInterpreter(serverOptions).toHttp(es)
  }

  // Needs to manually call killSwitch, because serverWithStop uses `allocated`
  override def server(
      routes: NonEmptyList[Routes[Any, Response]],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default
    val effect: ZIO[Scope, Throwable, Port] =
      (for {
        driver <- ZIO.service[Driver]
        result <- driver.start
        _ <- driver.addApp[Any](routes.toList.reduce(_ ++ _), ZEnvironment())
      } yield result.port)
        .provideSome[Scope](
          zio.test.driver,
          eventLoopGroup,
          channelFactory,
          ZLayer.succeed(
            Server.Config.default
              .port(0)
              .enableRequestStreaming
              .gracefulShutdownTimeout(gracefulShutdownTimeout.map(Duration.fromScala).getOrElse(50.millis))
          )
        )
    Resource.scoped[IO, Any, Port](effect)
  }

}
