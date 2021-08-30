package sttp.tapir.server.netty.example

import scala.concurrent.ExecutionContext

import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.{FullHttpRequest, HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler
import sttp.tapir.server.netty.NettyServerInterpreter.NettyRoutingResult

class NettyServerInitializer(val handlers: List[FullHttpRequest => NettyRoutingResult])(implicit val ec: ExecutionContext)
    extends ChannelInitializer[Channel] {

  def initChannel(ch: Channel) {
    val pipeline = ch.pipeline()
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
    pipeline.addLast(new NettyServerHandler(handlers))
    pipeline.addLast(new LoggingHandler())
  }
}
