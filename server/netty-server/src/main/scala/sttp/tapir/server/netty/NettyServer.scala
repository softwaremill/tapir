package sttp.tapir.server.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.socket.nio.NioServerSocketChannel
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.{NettyServerHandler, nettyChannelFutureToScala, nettyFutureToScala}

import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, Future}

// TODO: reduce routes to a single route in Route.reduce?
case class NettyServer(routes: Vector[Route], options: NettyServerOptions)(implicit ec: ExecutionContext) {
  def addEndpoint(se: ServerEndpoint[_, _, _, Any, Future]): NettyServer = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[_, _, _, Any, Future], overrideOptions: NettyServerOptions): NettyServer =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[_, _, _, Any, Future]]): NettyServer = addRoute(
    NettyServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[_, _, _, Any, Future]], overrideOptions: NettyServerOptions): NettyServer = addRoute(
    NettyServerInterpreter(overrideOptions).toRoute(ses)
  )

  def addRoute(r: Route): NettyServer = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[Route]): NettyServer = copy(routes = routes ++ r)

  def options(o: NettyServerOptions): NettyServer = copy(options = o)
  def host(s: String): NettyServer = copy(options = options.host(s))
  def port(p: Int): NettyServer = copy(options = options.port(p))

  def start(): Future[NettyServerBinding] = {
    val httpBootstrap = new ServerBootstrap()
    val eventLoopGroup = options.nettyOptions.eventLoopGroup()

    httpBootstrap
      .group(eventLoopGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit =
          options.nettyOptions.initPipeline(ch.pipeline(), new NettyServerHandler(routes.toList))
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128) //https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true) // https://github.com/netty/netty/issues/1692

    val channelFuture = httpBootstrap.bind(options.host, options.port)
    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyServerBinding(
        ch.localAddress().asInstanceOf[InetSocketAddress],
        () => stop(ch, eventLoopGroup)
      )
    )
  }

  private def stop(ch: Channel, eventLoopGroup: EventLoopGroup): Future[Unit] = {
    nettyFutureToScala(ch.close()).flatMap { _ =>
      if (options.nettyOptions.shutdownEventLoopGroupOnClose) {
        nettyFutureToScala(eventLoopGroup.shutdownGracefully()).map(_ => ())
      } else Future.successful(())
    }
  }
}

object NettyServer {
  def apply(serverOptions: NettyServerOptions = NettyServerOptions.default)(implicit ec: ExecutionContext): NettyServer =
    NettyServer(Vector.empty, serverOptions)
}

case class NettyServerBinding(localSocket: InetSocketAddress, stop: () => Future[Unit]) {
  def host: String = localSocket.getHostString
  def port: Int = localSocket.getPort
}
