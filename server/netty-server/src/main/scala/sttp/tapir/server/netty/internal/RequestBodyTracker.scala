package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}
import io.netty.util.AttributeKey

object RequestBodyTracker {
  val key = "tapir.requestbody.completed"

  val BodyComplete: AttributeKey[Boolean] = AttributeKey.valueOf[Boolean](key)
}

private[netty] class RequestBodyTrackerHandler extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case _: LastHttpContent => ctx.channel().attr(RequestBodyTracker.BodyComplete).set(true)
      case _: HttpRequest => ctx.channel().attr(RequestBodyTracker.BodyComplete).set(false)
      case _ => ()
    }
    ctx.fireChannelRead(msg)
  }
}
