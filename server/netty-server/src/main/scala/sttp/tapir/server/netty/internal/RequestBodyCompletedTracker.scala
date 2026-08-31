package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}

/** Has to be included in the pipeline before HttpStreamsServerHandler to observe LastHttpContent and HttpRequest
  */
object RequestBodyCompletedTracker {

  def wasRequestBodyFullyReceived(ctx: ChannelHandlerContext): Boolean =
    ctx.pipeline().get(classOf[RequestBodyCompletedTracker]) match {
      case t: RequestBodyCompletedTracker => t.bodyFullyReceived
      case _                              => true
    }
}

class RequestBodyCompletedTracker extends ChannelInboundHandlerAdapter {

  private[internal] var bodyFullyReceived: Boolean = true

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case _: LastHttpContent => bodyFullyReceived = true
      case _: HttpRequest     => bodyFullyReceived = false
      case _                  => ()
    }
    val _ = ctx.fireChannelRead(msg)
  }
}
