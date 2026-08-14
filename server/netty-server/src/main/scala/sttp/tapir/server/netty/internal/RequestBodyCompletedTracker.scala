package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}
import io.netty.util.AttributeKey

object RequestBodyCompletedTracker {
  val key = "tapir.requestbody.completed"

  val BodyComplete: AttributeKey[Boolean] = AttributeKey.valueOf[Boolean](key)

  def wasBodyCompletelySend(ctx: ChannelHandlerContext): Boolean =
    Option(ctx.channel().attr(RequestBodyCompletedTracker.BodyComplete).get()).contains(true)
}

private[netty] class RequestBodyCompletedTracker extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case _: LastHttpContent => ctx.channel().attr(RequestBodyCompletedTracker.BodyComplete).set(true)
      case _: HttpRequest => ctx.channel().attr(RequestBodyCompletedTracker.BodyComplete).set(false)
      case _ => ()
    }
    ctx.fireChannelRead(msg)
  }
}
