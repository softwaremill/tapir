package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}
import io.netty.util.AttributeKey

/** Has to be included in the pipeline before HttpStreamsServerHandler to observe LastHttpContent and HttpRequest
  */
object RequestBodyCompletedTracker {
  val key = "tapir.requestbody.completed"

  val BodyComplete: AttributeKey[Boolean] = AttributeKey.valueOf[Boolean](key)

  def wasRequestBodyFullyReceived(ctx: ChannelHandlerContext): Boolean =
    Option(ctx.channel().attr(RequestBodyCompletedTracker.BodyComplete).get()).getOrElse(true)
}

class RequestBodyCompletedTracker extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case _: LastHttpContent => ctx.channel().attr(RequestBodyCompletedTracker.BodyComplete).set(true)
      case _: HttpRequest     => ctx.channel().attr(RequestBodyCompletedTracker.BodyComplete).set(false)
      case _                  => ()
    }
    val _ = ctx.fireChannelRead(msg)
  }
}
