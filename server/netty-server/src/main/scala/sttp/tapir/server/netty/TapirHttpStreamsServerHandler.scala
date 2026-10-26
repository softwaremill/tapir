package sttp.tapir.server.netty

import io.netty.channel.{ChannelHandlerContext, ChannelPromise}
import io.netty.handler.codec.http.{FullHttpResponse, HttpContent, HttpRequest, HttpResponse, HttpUtil}
import org.playframework.netty.http.{HttpStreamsServerHandler, StreamedHttpRequest}
import org.reactivestreams.Publisher
import sttp.tapir.server.netty.internal.reactivestreams.TapirStreamedHttpRequest

/** A [[HttpStreamsServerHandler]] which also sends `100 Continue` if a request body is read only after the response headers are written
  * (e.g. when echoing a streamed body). Use it instead of [[HttpStreamsServerHandler]] when providing a custom
  * [[NettyConfig.initPipeline]]. Keeps per-connection state, so each pipeline needs its own instance.
  */
final class TapirHttpStreamsServerHandler extends HttpStreamsServerHandler {

  // How `100 Continue` is sent (#5503):
  // - the superclass, netty-reactive-streams' `HttpStreamsServerHandler`, sends it when the request body is first requested, so that
  //   clients don't upload bodies which are never read. If a response is written while the client still awaits a 100, the connection is
  //   closed after the response.
  // - request bodies read while decoding the inputs (bytes, strings, files, input streams) are requested before any response is written,
  //   so the superclass alone is enough
  // - request bodies read lazily (streams, `InputStreamRange`) might be read only after the response headers are written, e.g. when
  //   echoed. Such requests are marked when the body is given to the server logic (`NettyRequestBody.markBodyReadLazily`), and if the
  //   request awaiting a 100 is marked, the body is requested here, before writing response headers.

  // The request awaiting a 100. The superclass keeps only a flag for the newest request, so without this, a marked request could trigger a
  // 100 for a later, pipelined one, which might never read its body. Only used on the channel's event loop.
  private var requestAwaitingContinue: TapirStreamedHttpRequest = null

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // as in the superclass: a new request, or body content which the client sends anyway, means that a 100 is no longer awaited
    requestAwaitingContinue = null
    super.channelRead(ctx, msg)
  }

  override protected def createStreamedMessage(httpRequest: HttpRequest, stream: Publisher[HttpContent]): HttpRequest =
    super.createStreamedMessage(httpRequest, stream) match {
      case streamed: StreamedHttpRequest =>
        val request = new TapirStreamedHttpRequest(streamed)
        if (HttpUtil.is100ContinueExpected(httpRequest)) requestAwaitingContinue = request
        request
      case other => other
    }

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = {
    msg match {
      // a complete response: the request body won't be read after it, so no 100 is needed; has to be matched before HttpResponse
      case _: FullHttpResponse => ()
      case _: HttpResponse     =>
        if (requestAwaitingContinue != null && requestAwaitingContinue.isBodyReadLazily) {
          // the body might be read only after these headers, so requesting it now (with pipelining, the superclass delays the 100 until
          // the preceding responses are written)
          bodyRequested(ctx)
          requestAwaitingContinue = null
        }
      case _ => ()
    }
    super.write(ctx, msg, promise)
  }
}
