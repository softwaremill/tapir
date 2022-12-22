package sttp.tapir.server.netty.internal

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{Channel, ChannelFuture, ChannelInitializer, ChannelOption, EventLoopGroup}
import sttp.tapir.server.netty.NettyOptions

import java.net.SocketAddress

object NettyBootstrap {
  def apply[F[_]](
      nettyOptions: NettyOptions[_ <: SocketAddress],
      handler: => NettyServerHandler[F],
      eventLoopGroup: EventLoopGroup
  ): ChannelFuture = {
    val httpBootstrap = new ServerBootstrap()

    httpBootstrap
      .group(eventLoopGroup)
      .channel(nettyOptions.eventLoopConfig.serverChannel)
      .childHandler(new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = nettyOptions.initPipeline(ch.pipeline(), handler)
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128) // https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true) // https://github.com/netty/netty/issues/1692

    httpBootstrap.bind(nettyOptions.socketAddress)
  }
}
