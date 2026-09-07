package sttp.tapir.server.netty

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpRequest, LastHttpContent}

/** Tracks whether the body of the request currently being handled has been received in full, so that a firing request timeout can report
  * 408 (client stalled mid-upload) instead of 503 (server too slow to respond).
  *
  * Belongs after the HTTP codec and before `HttpStreamsServerHandler`, which replaces the [[HttpRequest]] / [[LastHttpContent]] messages
  * this relies on with a single streamed request. [[NettyConfig.defaultInitPipeline]] adds it whenever `requestTimeout` is set; a custom
  * [[NettyConfig.initPipeline]] has to add it itself.
  *
  * The state is per connection, not per request: with auto-read enabled, a pipelined request's headers can be decoded while the preceding
  * request is still being handled, resetting it. That preceding request's timeout is then reported as 408 rather than 503; only the status
  * code is affected.
  */
class RequestBodyCompletionTracker extends ChannelInboundHandlerAdapter {

  private var bodyFullyReceived: Boolean = true

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // FullHttpRequest is both an HttpRequest and a LastHttpContent, so LastHttpContent has to be matched first
    msg match {
      case _: LastHttpContent => bodyFullyReceived = true
      case _: HttpRequest     => bodyFullyReceived = false
      case _                  => ()
    }
    val _ = ctx.fireChannelRead(msg)
  }
}

object RequestBodyCompletionTracker {

  /** Whether the request currently being handled on `ctx`'s channel has been received in full. `true` if the pipeline has no
    * [[RequestBodyCompletionTracker]]: an unknown state is blamed on the server rather than on the client.
    */
  private[netty] def wasRequestBodyFullyReceived(ctx: ChannelHandlerContext): Boolean = {
    val tracker = ctx.pipeline().get(classOf[RequestBodyCompletionTracker])
    tracker == null || tracker.bodyFullyReceived
  }
}
