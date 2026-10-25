package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.{HttpContent, HttpHeaders, HttpMethod, HttpRequest, HttpUtil, HttpVersion}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Subscriber

/** A delegating [[StreamedHttpRequest]], which additionally:
  *   - tracks if the request body was ever subscribed to, so that an unused body can be discarded (#4539)
  *   - tracks if the request body was given to the server logic as a stream, which might be read only after the response headers are
  *     written (#5503)
  *   - fails the body stream if it ends before the declared `Content-Length` is received (#4169)
  *
  * @param request
  *   The request as created by `HttpStreamsServerHandler`.
  */
private[netty] class TapirStreamedHttpRequest(val request: StreamedHttpRequest) extends StreamedHttpRequest {

  @volatile private var subscribed: Boolean = false
  def wasSubscribed: Boolean = subscribed

  @volatile private var bodyStreamed: Boolean = false
  def markBodyStreamed(): Unit = bodyStreamed = true
  def isBodyStreamed: Boolean = bodyStreamed

  //

  override def subscribe(s: Subscriber[? >: HttpContent]): Unit = {
    subscribed = true
    val contentLength = HttpUtil.getContentLength(request, -1L)
    request.subscribe(if (contentLength >= 0) new ContentLengthCheckingSubscriber(contentLength, s) else s)
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
