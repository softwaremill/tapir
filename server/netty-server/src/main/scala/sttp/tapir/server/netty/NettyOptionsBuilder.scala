package sttp.tapir.server.netty

import io.netty.channel.unix.DomainSocketAddress
import io.netty.channel.{ChannelHandler, ChannelPipeline}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler
import sttp.tapir.server.netty.NettyOptions.EventLoopConfig

import java.net.InetSocketAddress
import java.nio.file.Paths
import java.util.UUID

object NettyOptionsBuilder {

  /** Starts configuring the netty pipeline. By default, there's no max content length limit, and the logging handler isn't added. */
  def start: ConfigurePipeline = ConfigurePipeline(Integer.MAX_VALUE, addLoggingHandler = false)

  /** Allows choosing the socket type, using the default pipeline. */
  def defaultPipeline: ChooseSocketType = start.chooseSocketType

  /** @param maxContentLength
    *   The max content length passed to the [[HttpObjectAggregator]] handler.
    * @param addLoggingHandler
    *   Should a [[LoggingHandler]] be used.
    */
  case class ConfigurePipeline(maxContentLength: Int, addLoggingHandler: Boolean) {
    def maxContentLength(max: Int): ConfigurePipeline = copy(maxContentLength = max)
    def withLoggingHandler: ConfigurePipeline = copy(addLoggingHandler = true)
    def withoutLoggingHandler: ConfigurePipeline = copy(addLoggingHandler = false)

    def chooseSocketType: ChooseSocketType = ChooseSocketType((pipeline, handler) => {
      pipeline.addLast(new HttpServerCodec())
      pipeline.addLast(new HttpObjectAggregator(maxContentLength))
      pipeline.addLast(handler)
      if (addLoggingHandler) pipeline.addLast(new LoggingHandler())
      ()
    })
  }

  case class ChooseSocketType(initPipeline: (ChannelPipeline, ChannelHandler) => Unit) {

    /** Use a TCP socket. The host/port can be specified later. */
    def tcp: NettyOptions[InetSocketAddress] = NettyOptions(
      new InetSocketAddress(NettyDefaults.DefaultHost, NettyDefaults.DefaultPort),
      EventLoopConfig.auto,
      shutdownEventLoopGroupOnClose = true,
      initPipeline
    )

    /** Use a domain socket. By default uses a temp file, but this can be configured later. */
    def domainSocket: NettyOptions[DomainSocketAddress] = {
      val tempFile = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)
      NettyOptions(
        new DomainSocketAddress(tempFile.toFile),
        EventLoopConfig.domainSocket,
        shutdownEventLoopGroupOnClose = true,
        initPipeline
      )
    }
  }
}
