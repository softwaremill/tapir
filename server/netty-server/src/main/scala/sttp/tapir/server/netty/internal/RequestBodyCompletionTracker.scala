package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}

object RequestBodyCompletionTracker {
  def wasRequestBodyFullyReceived(ctx: ChannelHandlerContext): Boolean = {
    val tracker = ctx.pipeline().get(classOf[RequestBodyCompletionTracker])
    tracker == null || tracker.bodyFullyReceived
  }
}

/** Tracks whether the body of the request currently being handled has been received in full, so that a request timeout can tell a client
  * which stalled mid-upload (408) from server logic which is too slow to respond (503).
  *
  * Has to be included in the pipeline after the HTTP codec and before `HttpStreamsServerHandler`, which replaces the individual
  * [[HttpRequest]] / [[LastHttpContent]] messages this relies on with a single streamed request.
  */
class RequestBodyCompletionTracker extends ChannelInboundHandlerAdapter {

  /** A plain var, as it's only ever touched on the channel's event loop: written by channelRead below, read (through
    * wasRequestBodyFullyReceived) by NettyServerHandler.userEventTriggered when the request timeout fires.
    */
  private[internal] var bodyFullyReceived: Boolean = true

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // the order of the cases matters: FullHttpRequest is both an HttpRequest and a LastHttpContent, and has to match the latter
    msg match {
      case _: LastHttpContent => bodyFullyReceived = true
      case _: HttpRequest     => bodyFullyReceived = false
      case _                  => ()
    }
    val _ = ctx.fireChannelRead(msg)
  }
}
