package sttp.tapir.server.netty

import scala.concurrent.ExecutionContext

import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler
import sttp.tapir.server.netty.NettyServerInterpreter.Route

class NettyServerInitializer(val handlers: List[Route])(implicit val ec: ExecutionContext) extends ChannelInitializer[Channel] {

  def initChannel(ch: Channel) {
    val pipeline = ch.pipeline()
    pipeline.addLast(new HttpServerCodec())
    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
    pipeline.addLast(new NettyServerHandler(handlers))
    pipeline.addLast(new LoggingHandler())
  }
}
