package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}

/** Tracks whether the body of the request currently being handled has been received in full, so that a request timeout can tell a client
  * which stalled mid-upload (408) from server logic which is too slow to respond (503).
  *
  * Has to be included in the pipeline after the HTTP codec and before `HttpStreamsServerHandler`, which replaces the individual
  * [[HttpRequest]] / [[LastHttpContent]] messages this relies on with a single streamed request.
  *
  * Tracked per connection, not per request: headers of a following request reset it, so a slow handler can report 408 instead of 503.
  */
class RequestBodyCompletionTracker extends ChannelInboundHandlerAdapter {

  /** A plain var, as it's only ever touched on the channel's event loop: written by channelRead below, read (through
    * wasRequestBodyFullyReceived) by NettyServerHandler.userEventTriggered when the request timeout fires.
    */
  private var bodyFullyReceived: Boolean = true

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

object RequestBodyCompletionTracker {

  /** Whether the request currently being handled on `ctx`'s channel has been received in full.
    *
    * Answers `true` if the pipeline has no [[RequestBodyCompletionTracker]] - an unknown state is reported as "received in full", so that a
    * timeout is blamed on the server rather than on the client.
    */
  def wasRequestBodyFullyReceived(ctx: ChannelHandlerContext): Boolean = {
    val tracker = ctx.pipeline().get(classOf[RequestBodyCompletionTracker])
    tracker == null || tracker.bodyFullyReceived
  }
}
