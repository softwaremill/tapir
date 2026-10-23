package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.{HttpContent, HttpHeaders, HttpMethod, HttpRequest, HttpUtil, HttpVersion}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.{Subscriber, Subscription}

/** A delegating [[StreamedHttpRequest]], which additionally:
  *   - tracks if the request body was ever subscribed to, so that an unused body can be discarded (#4539)
  *   - tracks if any of the request body was ever requested, which is when `HttpStreamsServerHandler` sends `100 Continue` (#5503)
  *   - fails the body stream if it ends before the declared `Content-Length` is received (#4169)
  */
private[netty] class TapirStreamedHttpRequest(request: StreamedHttpRequest) extends StreamedHttpRequest {

  @volatile private var subscribed: Boolean = false
  def wasSubscribed: Boolean = subscribed

  @volatile private var requested: Boolean = false
  def wasRequested: Boolean = requested

  //

  override def subscribe(s: Subscriber[? >: HttpContent]): Unit = {
    subscribed = true
    val contentLength = HttpUtil.getContentLength(request, -1L)
    val checking = if (contentLength >= 0) new ContentLengthCheckingSubscriber(contentLength, s) else s
    request.subscribe(new Subscriber[HttpContent] {
      override def onSubscribe(subscription: Subscription): Unit = checking.onSubscribe(new Subscription {
        override def request(n: Long): Unit = {
          requested = true
          subscription.request(n)
        }
        override def cancel(): Unit = subscription.cancel()
      })
      override def onNext(content: HttpContent): Unit = checking.onNext(content)
      override def onError(t: Throwable): Unit = checking.onError(t)
      override def onComplete(): Unit = checking.onComplete()
    })
  }

  //

  override def setDecoderResult(result: DecoderResult): Unit = request.setDecoderResult(result)
  override def decoderResult(): DecoderResult = request.decoderResult()
  override def protocolVersion(): HttpVersion = request.protocolVersion()
  override def getProtocolVersion(): HttpVersion = request.getProtocolVersion()
  override def headers(): HttpHeaders = request.headers()
  override def getDecoderResult(): DecoderResult = request.getDecoderResult()
  override def setProtocolVersion(version: HttpVersion): HttpRequest = request.setProtocolVersion(version)
  override def uri(): String = request.uri()
  override def getUri(): String = request.getUri()
  override def method(): HttpMethod = request.method()
  override def setUri(uri: String): HttpRequest = request.setUri(uri)
  override def getMethod(): HttpMethod = request.getMethod()
  override def setMethod(method: HttpMethod): HttpRequest = request.setMethod(method)
}
