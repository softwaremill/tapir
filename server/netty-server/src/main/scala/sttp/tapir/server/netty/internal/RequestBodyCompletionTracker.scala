package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}

/** Tracks whether the body of the request currently being handled has been received in full, so that a firing request timeout can report
  * 408 (client stalled mid-upload) instead of 503 (server too slow to respond). Has to be placed after the HTTP codec and before
  * `HttpStreamsServerHandler`, which merges the [[HttpRequest]] / [[LastHttpContent]] messages this relies on into a single streamed
  * request.
  *
  * The state is per connection, not per request: with auto-read enabled, a pipelined request's headers can be decoded while the preceding
  * request is still being handled, resetting it. That preceding request's timeout is then reported as 408 rather than 503; only the status
  * code is affected.
  */
private[netty] class RequestBodyCompletionTracker extends ChannelInboundHandlerAdapter {

  // The initial value is never read in practice: the request timeout is armed only once a request's headers have passed through this
  // handler, which sets the flag. `true` is the safe default, blaming the server (503) rather than the client (408).
  private var _bodyFullyReceived: Boolean = true

  def bodyFullyReceived: Boolean = _bodyFullyReceived

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // FullHttpRequest is both an HttpRequest and a LastHttpContent, so LastHttpContent has to be matched first
    msg match {
      case _: LastHttpContent => _bodyFullyReceived = true
      case _: HttpRequest     => _bodyFullyReceived = false
      case _                  => ()
    }
    val _ = ctx.fireChannelRead(msg)
  }
}
