package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPromise}
import io.netty.handler.codec.http.{HttpContent, HttpRequest, HttpResponse, HttpResponseStatus, HttpUtil, LastHttpContent}

/** Tracks whether the body of the request currently being handled has been received in full, and whether the client still awaits a
  * `100 Continue` before sending it. This allows a firing request timeout to report 408 (client stalled mid-upload) instead of 503 (server
  * too slow to respond). Has to be placed after the HTTP codec and before `HttpStreamsServerHandler`, which merges the [[HttpRequest]] /
  * [[LastHttpContent]] messages this relies on into a single streamed request.
  *
  * The state is per connection, not per request: with auto-read enabled, a pipelined request's headers can be decoded while the preceding
  * request is still being handled, resetting it. That preceding request's timeout is then reported as 408 rather than 503; only the status
  * code is affected.
  */
private[netty] class RequestBodyCompletionTracker extends ChannelDuplexHandler {

  // The initial value is never read in practice: the request timeout is armed only once a request's headers have passed through this
  // handler, which sets the flag. `true` is the safe default, blaming the server (503) rather than the client (408).
  private var _bodyFullyReceived: Boolean = true
  private var _continueAwaited: Boolean = false

  def bodyFullyReceived: Boolean = _bodyFullyReceived

  /** If true, the client is waiting for the server, so a missing body isn't the client's fault. */
  def continueAwaited: Boolean = _continueAwaited

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // FullHttpRequest is both an HttpRequest and a LastHttpContent, so LastHttpContent has to be matched first
    msg match {
      case _: LastHttpContent =>
        _bodyFullyReceived = true
        _continueAwaited = false
      case r: HttpRequest =>
        _bodyFullyReceived = false
        _continueAwaited = HttpUtil.is100ContinueExpected(r)
      case _: HttpContent => _continueAwaited = false // the client sends the body anyway
      case _              => ()
    }
    val _ = ctx.fireChannelRead(msg)
  }

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = {
    msg match {
      case r: HttpResponse if r.status() == HttpResponseStatus.CONTINUE => _continueAwaited = false
      case _                                                            => ()
    }
    val _ = ctx.write(msg, promise)
  }
}
