package sttp.tapir.server.netty.internal

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{Channel, ChannelFuture, ChannelInitializer, ChannelOption, EventLoopGroup}
import io.netty.handler.timeout.ReadTimeoutHandler
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
        override def initChannel(ch: Channel): Unit = nettyConfig.initPipeline(nettyConfig)(ch.pipeline().addLast(new ReadTimeoutHandler(nettyConfig.requestTimeout)), handler)
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, nettyConfig.socketBacklog) // https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, nettyConfig.socketKeepAlive) // https://github.com/netty/netty/issues/1692
      .childOption[java.lang.Integer](ChannelOption.SO_TIMEOUT, nettyConfig.socketTimeout)
      .childOption[java.lang.Integer](ChannelOption.SO_LINGER, nettyConfig.lingerTimeout)
      .childOption[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyConfig.connectionTimeout * 1000)

    httpBootstrap.bind(overrideSocketAddress.getOrElse(new InetSocketAddress(nettyConfig.host, nettyConfig.port)))
  }
}
