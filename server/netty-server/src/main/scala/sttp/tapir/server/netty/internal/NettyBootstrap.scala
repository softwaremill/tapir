package sttp.tapir.server.netty.internal

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import sttp.tapir.server.netty.NettyOptions

import java.net.SocketAddress

object NettyBootstrap {
  def apply[F[_]](
      nettyOptions: NettyOptions[_],
      handler: => NettyServerHandler[F],
      eventLoopGroup: EventLoopGroup,
      serverChannel: Class[_ <: ServerChannel],
      socketAddress: SocketAddress
  ): ChannelFuture = {
    val httpBootstrap = new ServerBootstrap()

    httpBootstrap
      .group(eventLoopGroup)
      .channel(serverChannel)
      .childHandler(new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = nettyOptions.initPipeline(ch.pipeline(), handler)
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128) // https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true) // https://github.com/netty/netty/issues/1692

    httpBootstrap.bind(socketAddress)
  }
}
