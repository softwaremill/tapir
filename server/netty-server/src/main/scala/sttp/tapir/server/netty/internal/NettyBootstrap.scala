package sttp.tapir.server.netty.internal

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{Channel, ChannelFuture, ChannelInitializer, ChannelOption, EventLoopGroup}
import sttp.tapir.server.netty.NettyConfig

import java.net.{InetSocketAddress, SocketAddress}

object NettyBootstrap {
  def apply[F[_]](
      nettyConfig: NettyConfig,
      handler: => NettyServerHandler[F],
      eventLoopGroup: EventLoopGroup,
      overrideSocketAddress: Option[SocketAddress]
  ): ChannelFuture = {
    val httpBootstrap = new ServerBootstrap()

    httpBootstrap
      .group(eventLoopGroup)
      .channel(nettyConfig.eventLoopConfig.serverChannel)
      .childHandler(new ChannelInitializer[Channel] {
        override def initChannel(ch: Channel): Unit = nettyConfig.initPipeline(nettyConfig)(ch.pipeline(), handler)
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, nettyConfig.socketBacklog) // https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, nettyConfig.socketKeepAlive) // https://github.com/netty/netty/issues/1692

    httpBootstrap.bind(overrideSocketAddress.getOrElse(new InetSocketAddress(nettyConfig.host, nettyConfig.port)))
  }
}
