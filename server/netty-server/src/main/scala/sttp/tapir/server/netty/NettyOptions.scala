package sttp.tapir.server.netty

import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerDomainSocketChannel, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup, KQueueServerDomainSocketChannel, KQueueServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.unix.DomainSocketAddress
import io.netty.channel.{ChannelHandler, ChannelPipeline, EventLoopGroup, ServerChannel}
import io.netty.handler.ssl.SslContext
import sttp.tapir.server.netty.NettyOptions.EventLoopConfig

import java.io.File
import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path

/** Netty configuration options. Default instances for TCP and domain sockets are available via the [[NettyOptions#default()]] companion
  * object and [[NettyOptions#defaultDomainSocket()]]. Full customisation is available via [[NettyOptionsBuilder]].
  *
  * @tparam SA
  *   the type of socket being used; can be either [[InetSocketAddress]] for TCP sockets (the most common case), or [[DomainSocketAddress]]
  *   for unix domain sockets.
  */
case class NettyOptions[SA <: SocketAddress](
    socketAddress: SA,
    eventLoopConfig: EventLoopConfig,
    shutdownEventLoopGroupOnClose: Boolean,
    initPipeline: (ChannelPipeline, ChannelHandler) => Unit
) {
  def host(hostname: String)(implicit saIsInetSocketAddress: SA =:= InetSocketAddress): NettyOptions[InetSocketAddress] =
    copy(new InetSocketAddress(hostname, saIsInetSocketAddress(socketAddress).getPort))

  def port(p: Int)(implicit saIsInetSocketAddress: SA =:= InetSocketAddress): NettyOptions[InetSocketAddress] =
    copy(new InetSocketAddress(saIsInetSocketAddress(socketAddress).getHostName, p))

  def randomPort(implicit saIsInetSocketAddress: SA =:= InetSocketAddress): NettyOptions[InetSocketAddress] =
    copy(new InetSocketAddress(saIsInetSocketAddress(socketAddress).getHostName, 0))

  def sslContext(sslContext: SslContext)(implicit saIsInetSocketAddress: SA =:= InetSocketAddress): NettyOptions[InetSocketAddress] = {
    val initPipelineWithSslContext: (ChannelPipeline, ChannelHandler) => Unit = { (pipeline, handler) =>
      pipeline.addLast(sslContext.newHandler(pipeline.channel().alloc()))
      initPipeline(pipeline, handler)
    }
    copy(initPipeline = initPipelineWithSslContext)
  }

  def domainSocketPath(path: Path)(implicit saIsDomainSocketAddres: SA =:= DomainSocketAddress): NettyOptions[DomainSocketAddress] =
    copy(new DomainSocketAddress(path.toFile))

  def domainSocketFile(file: File)(implicit saIsDomainSocketAddres: SA =:= DomainSocketAddress): NettyOptions[DomainSocketAddress] =
    copy(new DomainSocketAddress(file))

  def noShutdownOnClose: NettyOptions[SA] = copy(shutdownEventLoopGroupOnClose = false)

  def eventLoopConfig(elc: EventLoopConfig): NettyOptions[SA] = copy(eventLoopConfig = elc)

  def eventLoopGroup(elg: EventLoopGroup): NettyOptions[SA] = copy(eventLoopConfig = EventLoopConfig.useExisting(elg))
}

object NettyOptions {
  def default: NettyOptions[InetSocketAddress] = NettyOptionsBuilder.defaultPipeline.tcp
  def defaultDomainSocket: NettyOptions[DomainSocketAddress] = NettyOptionsBuilder.defaultPipeline.domainSocket

  case class EventLoopConfig(initEventLoopGroup: () => EventLoopGroup, serverChannel: Class[_ <: ServerChannel])

  object EventLoopConfig {
    def domainSocket: EventLoopConfig = if (Epoll.isAvailable) {
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
