package sttp.tapir.server.netty

import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerDomainSocketChannel, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup, KQueueServerDomainSocketChannel, KQueueServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelHandler, ChannelPipeline, EventLoopGroup, ServerChannel}
import sttp.tapir.server.netty.NettyOptions.EventLoopConfig

import java.net.SocketAddress

case class NettyOptions[S <: SocketAddress](
    socketAddress: S,
    eventLoopConfig: EventLoopConfig,
    shutdownEventLoopGroupOnClose: Boolean,
    initPipeline: (ChannelPipeline, ChannelHandler) => Unit
)

object NettyOptions {
  case class EventLoopConfig(builder: () => EventLoopGroup, serverChannel: Class[_ <: ServerChannel])

  object EventLoopConfig {
    def unixDomainSocket: EventLoopConfig = if (Epoll.isAvailable) {
      EventLoopConfig(() => new EpollEventLoopGroup(), classOf[EpollServerDomainSocketChannel])
    } else if (KQueue.isAvailable) {
      EventLoopConfig(() => new KQueueEventLoopGroup(), classOf[KQueueServerDomainSocketChannel])
    } else {
      throw new Exception("UnixDomainSocket request, but neither Epoll nor KQueue is available")
    }

    val nio: EventLoopConfig = EventLoopConfig(() => new NioEventLoopGroup(), classOf[NioServerSocketChannel])
    val epoll: EventLoopConfig = EventLoopConfig(() => new EpollEventLoopGroup(), classOf[EpollServerSocketChannel])
    val kqueue: EventLoopConfig = EventLoopConfig(() => new KQueueEventLoopGroup(), classOf[KQueueServerSocketChannel])

    def auto: EventLoopConfig = {
      if (Epoll.isAvailable) {
        epoll
      } else if (KQueue.isAvailable) {
        kqueue
      } else {
        nio
      }
    }

    def useExisting(g: EventLoopGroup): EventLoopConfig = {
      g match {
        case _: NioEventLoopGroup    => EventLoopConfig(() => g, classOf[NioServerSocketChannel])
        case _: EpollEventLoopGroup  => EventLoopConfig(() => g, classOf[EpollServerSocketChannel])
        case _: KQueueEventLoopGroup => EventLoopConfig(() => g, classOf[KQueueServerSocketChannel])
        case other                   => throw new Exception(s"Unexpected EventLoopGroup of class ${other.getClass} provided")
      }
    }
  }

}
