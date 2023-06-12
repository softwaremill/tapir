package sttp.tapir.server.netty.zio

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyOptions, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio.{CancelableFuture, Runtime, Task, Unsafe}

import java.net.InetSocketAddress

class NettyZioTestServerInterpreter[R](eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Task, Any, NettyZioServerOptions[Any, InetSocketAddress], Task[Route[Task]]] {
  override def route(es: List[ServerEndpoint[Any, Task]], interceptors: Interceptors): Task[Route[Task]] = {
    val serverOptions: NettyZioServerOptions[Any, InetSocketAddress] = interceptors(
      NettyZioServerOptions.customiseInterceptors
    ).options
    NettyZioServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[Task[Route[Task]]]): Resource[IO, Port] = {
    val options =
      NettyZioServerOptions
        .default[R]
        .nettyOptions(NettyOptions.default.eventLoopGroup(eventLoopGroup).randomPort.noShutdownOnClose)

    val runtime: Runtime[R] = Runtime.default.asInstanceOf[Runtime[R]]

    val server: CancelableFuture[NettyZioServerBinding[R, InetSocketAddress]] =
      Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(NettyZioServer(options).addRoutes(routes.toList).start()))

    Resource
      .make(IO.fromFuture(IO(server)))(binding =>
        IO.fromFuture(IO(Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(binding.stop()))))
      )
      .map(b => b.port)
  }
}
