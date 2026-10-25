package sttp.tapir.server.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpContent, HttpRequest, HttpUtil}
import org.playframework.netty.http.{HttpStreamsServerHandler, StreamedHttpRequest}
import org.reactivestreams.Publisher

/** A [[HttpStreamsServerHandler]] which also sends `100 Continue` if a request body is read only after the response headers are written
  * (e.g. when echoing a streamed body). Use it instead of [[HttpStreamsServerHandler]] when providing a custom
  * [[NettyConfig.initPipeline]].
  */
class TapirHttpStreamsServerHandler extends HttpStreamsServerHandler {

  // The library tracks only whether the newest request awaits `100 Continue`. Remembering which request that is, so that with pipelining,
  // an earlier request doesn't trigger a 100 for a later one. Only used on the channel's event loop.
  private var awaitingContinue: HttpRequest = null

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // as in the library: a new request, or body content which the client sends anyway, means that a 100 is no longer awaited
    awaitingContinue = null
    super.channelRead(ctx, msg)
  }

  override protected def createStreamedMessage(httpRequest: HttpRequest, stream: Publisher[HttpContent]): HttpRequest = {
    val streamed = super.createStreamedMessage(httpRequest, stream)
    if (HttpUtil.is100ContinueExpected(httpRequest)) awaitingContinue = streamed
    streamed
  }

  /** Sends `100 Continue`, if the client still awaits it for the given request; with pipelining, once the preceding responses are written.
    * Has to be called on the channel's event loop, before the request's response is written.
    *
    * @param ctx
    *   This handler's context.
    * @param request
    *   A request created by this handler.
    */
  private[netty] def continueIfExpected(ctx: ChannelHandlerContext, request: StreamedHttpRequest): Unit =
    if (request eq awaitingContinue) bodyRequested(ctx)
}
