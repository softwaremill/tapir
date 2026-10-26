package sttp.tapir.server.netty

import io.netty.channel.{ChannelHandlerContext, ChannelPromise}
import io.netty.handler.codec.http.{FullHttpResponse, HttpContent, HttpRequest, HttpResponse, HttpUtil}
import org.playframework.netty.http.{HttpStreamsServerHandler, StreamedHttpRequest}
import org.reactivestreams.Publisher
import sttp.tapir.server.netty.internal.reactivestreams.TapirStreamedHttpRequest

/** A [[HttpStreamsServerHandler]] which also sends `100 Continue` if a request body is read only after the response headers are written
  * (e.g. when echoing a streamed body). Use it instead of [[HttpStreamsServerHandler]] when providing a custom
  * [[NettyConfig.initPipeline]].
  */
class TapirHttpStreamsServerHandler extends HttpStreamsServerHandler {

  // How `100 Continue` is sent (#5503):
  // - the library sends it when the request body is first requested, so that clients don't upload bodies which are never read. If a
  //   response is written while the client still awaits a 100, the connection is closed after the response.
  // - request bodies read while decoding the inputs (bytes, strings, files, input streams) are requested before any response is written,
  //   so the library alone is enough
  // - request bodies read lazily (streams, `InputStreamRange`) might be read only after the response headers are written, e.g. when
  //   echoed. Such requests are marked when the body is given to the server logic (`NettyRequestBody.markBodyReadLazily`), and if the
  //   request awaiting a 100 is marked, the body is requested here, before writing response headers.

  // The library tracks only whether the newest request awaits a 100. Remembering which request that is, so that a marked request doesn't
  // trigger a 100 for a later, pipelined one, which might never read its body. Only used on the channel's event loop.
  private var awaitingContinue: TapirStreamedHttpRequest = null

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // as in the library: a new request, or body content which the client sends anyway, means that a 100 is no longer awaited
    awaitingContinue = null
    super.channelRead(ctx, msg)
  }

  override protected def createStreamedMessage(httpRequest: HttpRequest, stream: Publisher[HttpContent]): HttpRequest =
    super.createStreamedMessage(httpRequest, stream) match {
      case streamed: StreamedHttpRequest =>
        val request = new TapirStreamedHttpRequest(streamed)
        if (HttpUtil.is100ContinueExpected(httpRequest)) awaitingContinue = request
        request
      case other => other
    }

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = {
    msg match {
      case _: FullHttpResponse => () // a body needed to create a complete response was already requested, and it won't be read later
      case _: HttpResponse if awaitingContinue != null && awaitingContinue.isBodyReadLazily =>
        // with pipelining, the library sends the 100 once the preceding responses are written
        bodyRequested(ctx)
        awaitingContinue = null
      case _ => ()
    }
    super.write(ctx, msg, promise)
  }
}
