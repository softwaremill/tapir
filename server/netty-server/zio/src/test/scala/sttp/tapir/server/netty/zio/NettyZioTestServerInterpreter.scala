package sttp.tapir.server.netty.zio

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio.{CancelableFuture, Runtime, Task, Unsafe}

import java.net.InetSocketAddress

class NettyZioTestServerInterpreter[R](eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Task, Any, NettyZioServerOptions[Any], Task[Route[Task]]] {
  override def route(es: List[ServerEndpoint[Any, Task]], interceptors: Interceptors): Task[Route[Task]] = {
    val serverOptions = interceptors(
      NettyZioServerOptions.customiseInterceptors
    ).options
    NettyZioServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[Task[Route[Task]]]): Resource[IO, Port] = {
    val config = NettyConfig.defaultWithStreaming.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose
    val options = NettyZioServerOptions.default[R]

    val runtime: Runtime[R] = Runtime.default.asInstanceOf[Runtime[R]]

    val server: CancelableFuture[NettyZioServerBinding[R]] =
      Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(NettyZioServer(options, config).addRoutes(routes.toList).start()))

    Resource
      .make(IO.fromFuture(IO(server)))(binding =>
        IO.fromFuture(IO(Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(binding.stop()))))
      )
      .map(b => b.port)
  }
}
