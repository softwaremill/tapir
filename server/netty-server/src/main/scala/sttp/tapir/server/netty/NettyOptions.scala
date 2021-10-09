package sttp.tapir.server.netty

import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup, KQueueServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelHandler, ChannelPipeline, EventLoopGroup, ServerChannel}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler
import sttp.tapir.server.netty.NettyOptions.EventLoopConfig

case class NettyOptions(
    eventLoopConfig: NettyOptions.EventLoopConfig,
    shutdownEventLoopGroupOnClose: Boolean,
    initPipeline: (ChannelPipeline, ChannelHandler) => Unit
) {
  def shutdownEventLoopGroupOnClose(shutdown: Boolean): NettyOptions = copy(shutdownEventLoopGroupOnClose = shutdown)
  def eventLoopConfig(g: () => EventLoopGroup, clz: Class[_ <: ServerChannel]): NettyOptions =
    copy(eventLoopConfig = EventLoopConfig(g, clz), shutdownEventLoopGroupOnClose = false)
  def eventLoopGroup(g: EventLoopGroup): NettyOptions = {
    g match {
      case _: NioEventLoopGroup    => eventLoopConfig(() => g, classOf[NioServerSocketChannel])
      case _: EpollEventLoopGroup  => eventLoopConfig(() => g, classOf[EpollServerSocketChannel])
      case _: KQueueEventLoopGroup => eventLoopConfig(() => g, classOf[KQueueServerSocketChannel])
      case other                   => throw new Exception(s"Unexpected EventLoopGroup of class ${other.getClass} provided")
    }
  }
  def eventLoopConfig(cfg: EventLoopConfig): NettyOptions = copy(eventLoopConfig = cfg)
}

object NettyOptions {
  def default: NettyOptions = NettyOptions(
    EventLoopConfig.auto,
    shutdownEventLoopGroupOnClose = true,
    (pipeline, handler) => {
      pipeline.addLast(new HttpServerCodec())
      pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
      pipeline.addLast(handler)
      pipeline.addLast(new LoggingHandler()) // TODO
      ()
    }
  )

  case class EventLoopConfig(builder: () => EventLoopGroup, serverChannel: Class[_ <: ServerChannel])

  object EventLoopConfig {
    def nio: EventLoopConfig = EventLoopConfig(() => new NioEventLoopGroup(), classOf[NioServerSocketChannel])
    def epoll: EventLoopConfig = EventLoopConfig(() => new EpollEventLoopGroup(), classOf[EpollServerSocketChannel])
    def kqueue: EventLoopConfig = EventLoopConfig(() => new KQueueEventLoopGroup(), classOf[KQueueServerSocketChannel])

    def auto: EventLoopConfig = {
      if (Epoll.isAvailable) {
        epoll
      } else if (KQueue.isAvailable) {
        kqueue
      } else {
        nio
      }
    }
  }

}
