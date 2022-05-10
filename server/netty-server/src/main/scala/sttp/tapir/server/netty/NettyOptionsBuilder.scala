package sttp.tapir.server.netty

import io.netty.channel.unix.DomainSocketAddress
import io.netty.channel.{ChannelHandler, ChannelPipeline, EventLoopGroup}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler
import sttp.tapir.server.netty.NettyOptions.EventLoopConfig
import sttp.tapir.server.netty.NettyOptionsBuilder.{DomainSocketOptionsBuilder, TcpOptionsBuilder}

import java.net.InetSocketAddress
import java.nio.file.{Path, Paths}
import java.util.UUID

case class NettyOptionsBuilder(initPipeline: (ChannelPipeline, ChannelHandler) => Unit) {
  def tcp(): TcpOptionsBuilder = TcpOptionsBuilder(this, NettyDefaults.DefaultHost, NettyDefaults.DefaultPort)
  def domainSocket(): DomainSocketOptionsBuilder = DomainSocketOptionsBuilder(this, tempFile)

  private def tempFile = {
    Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)
  }
}

object NettyOptionsBuilder {
  private val defaultInitPipeline: (ChannelPipeline, ChannelHandler) => Unit = (pipeline, handler) => {
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
    pipeline.addLast(handler)
    pipeline.addLast(new LoggingHandler()) // TODO
    ()
  }
  def make(): NettyOptionsBuilder = NettyOptionsBuilder(defaultInitPipeline)

  def default: TcpOptionsBuilder = make().tcp()
  def domainSocket: DomainSocketOptionsBuilder = make().domainSocket()

  case class DomainSocketOptionsBuilder(
      netty: NettyOptionsBuilder,
      path: Path,
      shutdownOnClose: Boolean = true,
      eventLoopConfig: EventLoopConfig = EventLoopConfig.domainSocket
  ) {
    def path(path: Path) = copy(path = path)
    def eventLoopGroup(g: EventLoopGroup): DomainSocketOptionsBuilder =
      copy(eventLoopConfig = EventLoopConfig.useExisting(g), shutdownOnClose = true)
    def noShutdownOnClose: DomainSocketOptionsBuilder = copy(shutdownOnClose = false)
    def build: NettyOptions = NettyOptions(
      new DomainSocketAddress(path.toFile),
      EventLoopConfig.domainSocket,
      shutdownOnClose,
      netty.initPipeline
    )
  }

  case class TcpOptionsBuilder(
      netty: NettyOptionsBuilder,
      host: String,
      port: Int,
      shutdownOnClose: Boolean = true,
      eventLoopConfig: EventLoopConfig = EventLoopConfig.auto
  ) {
    def host(host: String): TcpOptionsBuilder = copy(host = host)
    def port(port: Int): TcpOptionsBuilder = copy(port = port)
    def randomPort: TcpOptionsBuilder = copy(port = 0)
    def noShutdownOnClose: TcpOptionsBuilder = copy(shutdownOnClose = false)
    def eventLoopGroup(g: EventLoopGroup): TcpOptionsBuilder =
      copy(eventLoopConfig = EventLoopConfig.useExisting(g), shutdownOnClose = true)
    def build: NettyOptions = NettyOptions(
      new InetSocketAddress(host, port),
      eventLoopConfig,
      shutdownOnClose,
      netty.initPipeline
    )
  }

}
