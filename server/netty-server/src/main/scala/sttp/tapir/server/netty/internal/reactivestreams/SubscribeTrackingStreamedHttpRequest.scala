package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.{HttpContent, HttpHeaders, HttpMethod, HttpRequest, HttpVersion}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Subscriber

/** A delegating [[StreamedHttpRequest]] which additionally tracks if the request body was ever subscribed to (see #4539). */
class SubscribeTrackingStreamedHttpRequest(request: StreamedHttpRequest) extends StreamedHttpRequest {

  @volatile private var subscribed: Boolean = false
  def wasSubscribed: Boolean = subscribed

  //

  override def subscribe(s: Subscriber[? >: HttpContent]): Unit = {
    subscribed = true
    request.subscribe(s)
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
