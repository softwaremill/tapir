package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio._
import zio.http._
import zio.http.netty.server.NettyDriver
import zio.interop.catz._

import java.net.InetAddress

class ZioHttpTestServerInterpreter(implicit trace: Trace)
    extends TestServerInterpreter[Task, ZioStreams, ZioHttpServerOptions[Any], Http[Any, Throwable, Request, Response]] {

  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): Http[Any, Throwable, Request, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = interceptors(ZioHttpServerOptions.customiseInterceptors).options
    ZioHttpInterpreter(serverOptions).toHttp(es)
  }

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default

    val io: ZIO[Scope, Throwable, Int] =
      (for {
        driver <- ZIO.service[Driver]
        _ <- driver.start(trace)
        _ <- Server.serve(routes.toList.reduce(_ ++ _)).provide(ZLayer.succeed(driver) >>> Server.base).forkScoped
      } yield 0).provide(Scope.default, ServerConfig.live(ServerConfig(address = new InetSocketAddress(0))), NettyDriver.default)

    Resource.scoped[IO, Any, Int](io)
  }
}
