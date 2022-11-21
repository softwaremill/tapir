package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio._
import zio.http.netty.server.NettyDriver
import zio.http.service.ServerChannelFactory
import zio.http._
import zio.interop.catz._

class ZioHttpTestServerInterpreter(eventLoopGroup: zio.http.service.EventLoopGroup, channelFactory: zio.http.service.ServerChannelFactory)(
    implicit trace: Trace
) extends TestServerInterpreter[Task, ZioStreams, ZioHttpServerOptions[Any], Http[Any, Throwable, Request, Response]] {

  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): Http[Any, Throwable, Request, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = interceptors(ZioHttpServerOptions.customiseInterceptors).options
    ZioHttpInterpreter(serverOptions).toHttp(es)
  }

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default

    val makeNettyDriver = {
      import zio.http.netty.server._
      import io.netty.bootstrap.ServerBootstrap
      import io.netty.channel._
      import io.netty.util.ResourceLeakDetector
      import zio._
      import zio.http.netty._
      import zio.http.service.ServerTime
      import zio.http.{Driver, Http, HttpApp, Server, ServerConfig}

      import java.net.InetSocketAddress
      import java.util.concurrent.atomic.AtomicReference

      type ErrorCallbackRef = AtomicReference[Option[Server.ErrorCallback]]
      type AppRef = AtomicReference[(HttpApp[Any, Throwable], ZEnvironment[Any])]
      type EnvRef = AtomicReference[ZEnvironment[Any]]

      val app = ZLayer.succeed(
        new AtomicReference[(HttpApp[Any, Throwable], ZEnvironment[Any])]((Http.empty, ZEnvironment.empty))
      )
      val ecb = ZLayer.succeed(new AtomicReference[Option[Server.ErrorCallback]](Option.empty))
      val time = ZLayer.succeed(ServerTime.make(1000.millis))

      val nettyRuntime = NettyRuntime.usingSharedThreadPool
      val serverChannelInitializer = ServerChannelInitializer.layer
      val serverInboundHandler = ServerInboundHandler.layer

      val serverLayers = app ++
        ZLayer.succeed(channelFactory) ++
        (
          (
            (time ++ app ++ ecb) ++
              (ZLayer.succeed(eventLoopGroup) >>> nettyRuntime) >>> serverInboundHandler
          ) >>> serverChannelInitializer
        ) ++
        ecb ++
        ZLayer.succeed(eventLoopGroup)

      NettyDriver.make.provideSomeLayer[ServerConfig with Scope](serverLayers)
    }

    val effect: ZIO[Scope, Throwable, Int] =
      (for {
        driver <- makeNettyDriver
        port <- driver.start(trace)
        _ <- driver.addApp[Any](routes.toList.reduce(_ ++ _), ZEnvironment())
      } yield port).provideSome[Scope](ServerConfig.live(ServerConfig.default.port(0)))

    Resource.scoped[IO, Any, Int](effect)
  }
}
