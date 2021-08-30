package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.netty.NettyServerInterpreter.NettyRoutingResult
import sttp.tapir.server.netty.example.NettyServerInitializer
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class NettyTestServerInterpreter(workers: NioEventLoopGroup, acceptors: NioEventLoopGroup)(implicit ec: ExecutionContext)
    extends TestServerInterpreter[Future, Any, FullHttpRequest => NettyRoutingResult] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): (FullHttpRequest => NettyRoutingResult) = {
    val serverOptions: NettyServerOptions = NettyServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options

    NettyServerInterpreter(serverOptions).toHandler(List(e))
  }

  override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Any, Future]]): FullHttpRequest => NettyRoutingResult = {
    NettyServerInterpreter().toHandler(es)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): FullHttpRequest => NettyRoutingResult = {
    NettyServerInterpreter().toHandler(List(e.serverLogicRecoverErrors(fn)))
  }

  override def server(routes: NonEmptyList[FullHttpRequest => NettyRoutingResult]): Resource[IO, Port] = {
    val bind = IO.fromFuture({
      val httpBootstrap = new ServerBootstrap()

      httpBootstrap
        .group(acceptors, workers)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new NettyServerInitializer(routes.toList))
        .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128) //https://github.com/netty/netty/issues/1692
        .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true) // https://github.com/netty/netty/issues/1692

      // Bind and start to accept incoming connections.
      val httpChannel = httpBootstrap.bind(0).sync

      IO(Future(httpChannel))
    })

    Resource
      .make(bind)(binding => IO.fromFuture(IO(Future(binding.channel.closeFuture))).void)
      .map(channelFuture => {
        channelFuture
          .channel()
          .localAddress()
          .toString
          .split(":")
          .toList
          .last
          .toInt
      })
  }
}
