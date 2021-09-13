package sttp.tapir.server.netty

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.{ChannelPipeline, EventLoopGroup}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler

case class NettyOptions(
    eventLoopGroup: () => EventLoopGroup,
    shutdownEventLoopGroupOnClose: Boolean,
    initPipeline: (ChannelPipeline, NettyServerHandler) => Unit
) {
  def shutdownEventLoopGroupOnClose(shutdown: Boolean): NettyOptions = copy(shutdownEventLoopGroupOnClose = shutdown)
  def eventLoopGroup(g: EventLoopGroup): NettyOptions = copy(eventLoopGroup = () => g, shutdownEventLoopGroupOnClose = false)
  def eventLoopGroup(g: () => EventLoopGroup): NettyOptions = copy(eventLoopGroup = g)
}

object NettyOptions {
  def default: NettyOptions = NettyOptions(
    () => new NioEventLoopGroup(),
    shutdownEventLoopGroupOnClose = true,
    (pipeline, handler) => {
      pipeline.addLast(new HttpServerCodec())
      pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
      pipeline.addLast(handler)
      pipeline.addLast(new LoggingHandler()) // TODO
    }
  )
}
