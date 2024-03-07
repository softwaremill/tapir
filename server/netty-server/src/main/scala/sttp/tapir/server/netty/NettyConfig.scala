package sttp.tapir.server.netty

import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup, KQueueServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelHandler, ChannelPipeline, EventLoopGroup, ServerChannel}
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import org.playframework.netty.http.HttpStreamsServerHandler
import sttp.tapir.server.netty.NettyConfig.EventLoopConfig

import scala.concurrent.duration._

/** Netty configuration, used by [[NettyFutureServer]] and other server implementations to configure the networking layer, the Netty
  * processing pipeline, and start & stop the server.
  *
  * @param maxConnections
  *   The maximum number of concurrent connections allowed by the server. Any connections above this limit will be closed right after they
  *   are opened.
  *
  * @param addLoggingHandler
  *   Should a low-level logging handler, [[io.netty.handler.logging.LoggingHandler]], be added to the Netty pipeline. This is independent
  *   from the logging interceptor configured through the server options.
  *
  * @param initPipeline
  *   The function to create the Netty pipeline, using the configuration instance, the pipeline created so far, and the handler which
  *   contains tapir's server processing logic.
  *
  * @param requestTimeout
  *   The maximum duration, to wait for a response before considering the request timed out.
  * @throws ReadTimeoutException
  *   when no data is read from Netty within the specified period of time for a request.
  *
  * @param connectionTimeout
  *   Specifies the maximum duration within which a connection between a client and a server must be established.
  *
  * @param lingerTimeout
  *   Sets the delay for which the Netty waits, while data is being transmitted, before closing a socket after receiving a call to close the
  *   socket
  *
  * @param gracefulShutdownTimeout
  *   If set, attempts to wait for a given time for all in-flight requests to complete, before proceeding with shutting down the server. If
  *   `None`, closes the channels and terminates the server without waiting.
  */
case class NettyConfig(
    host: String,
    port: Int,
    shutdownEventLoopGroupOnClose: Boolean,
    maxConnections: Option[Int],
    socketBacklog: Int,
    requestTimeout: Option[FiniteDuration],
    connectionTimeout: Option[FiniteDuration],
    lingerTimeout: Option[FiniteDuration],
    socketKeepAlive: Boolean,
    addLoggingHandler: Boolean,
    sslContext: Option[SslContext],
    eventLoopConfig: EventLoopConfig,
    socketConfig: NettySocketConfig,
    initPipeline: NettyConfig => (ChannelPipeline, ChannelHandler) => Unit,
    gracefulShutdownTimeout: Option[FiniteDuration]
) {
  def host(h: String): NettyConfig = copy(host = h)

  def port(p: Int): NettyConfig = copy(port = p)
  def randomPort: NettyConfig = copy(port = 0)

  def withShutdownEventLoopGroupOnClose: NettyConfig = copy(shutdownEventLoopGroupOnClose = true)
  def withDontShutdownEventLoopGroupOnClose: NettyConfig = copy(shutdownEventLoopGroupOnClose = false)

  def maxConnections(m: Int): NettyConfig = copy(maxConnections = Some(m))

  def socketBacklog(s: Int): NettyConfig = copy(socketBacklog = s)

  def requestTimeout(r: FiniteDuration): NettyConfig = copy(requestTimeout = Some(r))
  def connectionTimeout(c: FiniteDuration): NettyConfig = copy(connectionTimeout = Some(c))
  def lingerTimeout(l: FiniteDuration): NettyConfig = copy(requestTimeout = Some(l))

  def withSocketKeepAlive: NettyConfig = copy(socketKeepAlive = true)
  def withNoSocketKeepAlive: NettyConfig = copy(socketKeepAlive = false)

  def withAddLoggingHandler: NettyConfig = copy(addLoggingHandler = true)

  def withNoLoggingHandler: NettyConfig = copy(addLoggingHandler = false)

  def sslContext(s: SslContext): NettyConfig = copy(sslContext = Some(s))
  def noSslContext: NettyConfig = copy(sslContext = None)

  def socketConfig(config: NettySocketConfig) = copy(socketConfig = config)

  def eventLoopConfig(elc: EventLoopConfig): NettyConfig = copy(eventLoopConfig = elc)
  def eventLoopGroup(elg: EventLoopGroup): NettyConfig = copy(eventLoopConfig = EventLoopConfig.useExisting(elg))

  def initPipeline(f: NettyConfig => (ChannelPipeline, ChannelHandler) => Unit): NettyConfig = copy(initPipeline = f)

  def withGracefulShutdownTimeout(t: FiniteDuration) = copy(gracefulShutdownTimeout = Some(t))
  def noGracefulShutdown = copy(gracefulShutdownTimeout = None)
}

object NettyConfig {
  def default: NettyConfig = NettyConfig(
    host = "localhost",
    port = 8080,
    shutdownEventLoopGroupOnClose = true,
    socketBacklog = 128,
    socketKeepAlive = true,
    requestTimeout = Some(20.seconds),
    connectionTimeout = Some(10.seconds),
    lingerTimeout = None, // see #3576
    gracefulShutdownTimeout = Some(10.seconds),
    maxConnections = None,
    addLoggingHandler = false,
    sslContext = None,
    eventLoopConfig = EventLoopConfig.auto,
    socketConfig = NettySocketConfig.default,
    initPipeline = cfg => defaultInitPipeline(cfg)(_, _)
  )

  def defaultInitPipeline(cfg: NettyConfig)(pipeline: ChannelPipeline, handler: ChannelHandler): Unit = {
    cfg.sslContext.foreach(s => pipeline.addLast(s.newHandler(pipeline.channel().alloc())))
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpStreamsServerHandler())
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
