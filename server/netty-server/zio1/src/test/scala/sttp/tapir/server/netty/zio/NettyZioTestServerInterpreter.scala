package sttp.tapir.server.netty.zio

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyOptions, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio.{Runtime, Task}

import java.net.InetSocketAddress

class NettyZioTestServerInterpreter[R](eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Task, Any, NettyZioServerOptions[Any, InetSocketAddress], Route[Task]] {
  implicit val runtime: Runtime[R] = Runtime.default.asInstanceOf[Runtime[R]]

  override def route(es: List[ServerEndpoint[Any, Task]], interceptors: Interceptors): Route[Task] = {
    val serverOptions: NettyZioServerOptions[Any, InetSocketAddress] = interceptors(
      NettyZioServerOptions.customiseInterceptors
    ).options
    NettyZioServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[Route[Task]]): Resource[IO, Port] = {
    val options =
      NettyZioServerOptions
        .default[R]
        .nettyOptions(NettyOptions.default.eventLoopGroup(eventLoopGroup).randomPort.noShutdownOnClose)

    val server: NettyZioServerBinding[R, InetSocketAddress] =
      runtime.unsafeRun(NettyZioServer(options).addRoutes(routes.toList).start())

    Resource
      .make(IO(server))(binding => IO(runtime.unsafeRun(binding.stop())))
      .map(b => b.port)
  }
}
