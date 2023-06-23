package sttp.tapir.server.netty

import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup, KQueueServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelHandler, ChannelPipeline, EventLoopGroup, ServerChannel}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.stream.ChunkedWriteHandler
import sttp.tapir.server.netty.NettyConfig.EventLoopConfig
import scala.concurrent.duration._

/** Netty configuration, used by [[NettyFutureServer]] and other server implementations to configure the networking layer, the Netty
  * processing pipeline, and start & stop the server.
  *
  * @param maxContentLength
  *   The max content length passed to the [[io.netty.handler.codec.http.HttpObjectAggregator]] handler.
  *
  * @param addLoggingHandler
  *   Should a low-level logging handler, [[io.netty.handler.logging.LoggingHandler]], be added to the Netty pipeline. This is independent
  *   from the logging interceptor configured through the server options.
  *
  * @param initPipeline
  *   The function to create the Netty pipeline, using the configuration instance, the pipeline created so far, and the handler which
  *   contains tapir's server processing logic.
  */
case class NettyConfig(
    host: String,
    port: Int,
    shutdownEventLoopGroupOnClose: Boolean,
    maxContentLength: Int,
    socketBacklog: Int,
    requestTimeout: FiniteDuration,
    connectionTimeout: FiniteDuration,
    socketTimeout: FiniteDuration,
    lingerTimeout: FiniteDuration,
    socketKeepAlive: Boolean,
    addLoggingHandler: Boolean,
    sslContext: Option[SslContext],
    eventLoopConfig: EventLoopConfig,
    initPipeline: NettyConfig => (ChannelPipeline, ChannelHandler) => Unit
) {
  def host(h: String): NettyConfig = copy(host = h)

  def port(p: Int): NettyConfig = copy(port = p)
  def randomPort: NettyConfig = copy(port = 0)

  def withShutdownEventLoopGroupOnClose: NettyConfig = copy(shutdownEventLoopGroupOnClose = true)
  def withDontShutdownEventLoopGroupOnClose: NettyConfig = copy(shutdownEventLoopGroupOnClose = false)

  def maxContentLength(m: Int): NettyConfig = copy(maxContentLength = m)
  def noMaxContentLength: NettyConfig = copy(maxContentLength = Integer.MAX_VALUE)

  def socketBacklog(s: Int): NettyConfig = copy(socketBacklog = s)

  def withSocketKeepAlive: NettyConfig = copy(socketKeepAlive = true)
  def withNoSocketKeepAlive: NettyConfig = copy(socketKeepAlive = false)

  def withAddLoggingHandler: NettyConfig = copy(addLoggingHandler = true)

  def withNoLoggingHandler: NettyConfig = copy(addLoggingHandler = false)

  def sslContext(s: SslContext): NettyConfig = copy(sslContext = Some(s))
  def noSslContext: NettyConfig = copy(sslContext = None)

  def eventLoopConfig(elc: EventLoopConfig): NettyConfig = copy(eventLoopConfig = elc)
  def eventLoopGroup(elg: EventLoopGroup): NettyConfig = copy(eventLoopConfig = EventLoopConfig.useExisting(elg))

  def initPipeline(f: NettyConfig => (ChannelPipeline, ChannelHandler) => Unit): NettyConfig = copy(initPipeline = f)
}

object NettyConfig {
  def default: NettyConfig = NettyConfig(
    host = "localhost",
    port = 8080,
    shutdownEventLoopGroupOnClose = true,
    socketBacklog = 128,
    socketKeepAlive = true,
    requestTimeout = 60.seconds,
    connectionTimeout = 60.seconds,
    socketTimeout = 60.seconds,
    lingerTimeout = 1.second,
    maxContentLength = Integer.MAX_VALUE,
    addLoggingHandler = false,
    sslContext = None,
    eventLoopConfig = EventLoopConfig.auto,
    initPipeline = cfg => defaultInitPipeline(cfg)(_, _)
  )

  def defaultInitPipeline(cfg: NettyConfig)(pipeline: ChannelPipeline, handler: ChannelHandler): Unit = {
    cfg.sslContext.foreach(s => pipeline.addLast(s.newHandler(pipeline.channel().alloc())))
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpObjectAggregator(cfg.maxContentLength))
    pipeline.addLast(new ChunkedWriteHandler())
    pipeline.addLast(handler)
    if (cfg.addLoggingHandler) pipeline.addLast(new LoggingHandler())
    ()
  }

  case class EventLoopConfig(initEventLoopGroup: () => EventLoopGroup, serverChannel: Class[_ <: ServerChannel])

  object EventLoopConfig {
    val nio: EventLoopConfig = EventLoopConfig(() => new NioEventLoopGroup(), classOf[NioServerSocketChannel])
    val epoll: EventLoopConfig = EventLoopConfig(() => new EpollEventLoopGroup(), classOf[EpollServerSocketChannel])
    val kqueue: EventLoopConfig = EventLoopConfig(() => new KQueueEventLoopGroup(), classOf[KQueueServerSocketChannel])

    def auto: EventLoopConfig = if (Epoll.isAvailable) {
      epoll
    } else if (KQueue.isAvailable) {
      kqueue
    } else {
      nio
    }

    def useExisting(g: EventLoopGroup): EventLoopConfig = g match {
      case _: NioEventLoopGroup    => EventLoopConfig(() => g, classOf[NioServerSocketChannel])
      case _: EpollEventLoopGroup  => EventLoopConfig(() => g, classOf[EpollServerSocketChannel])
      case _: KQueueEventLoopGroup => EventLoopConfig(() => g, classOf[KQueueServerSocketChannel])
      case other                   => throw new Exception(s"Unexpected EventLoopGroup of class ${other.getClass} provided")
    }
  }
}
