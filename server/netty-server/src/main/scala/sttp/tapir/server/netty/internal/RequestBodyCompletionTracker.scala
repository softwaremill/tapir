package sttp.tapir.server.netty.internal

import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPromise}
import io.netty.handler.codec.http.{HttpContent, HttpRequest, HttpResponse, HttpResponseStatus, HttpStatusClass, HttpUtil, LastHttpContent}

import scala.collection.mutable

/** Tracks, for each request whose response hasn't started yet, whether its body has been received in full, and whether the client still
  * awaits a `100 Continue` before sending it. This allows a firing request timeout to report 408 (client stalled mid-upload) instead of 503
  * (server too slow to respond). Has to be placed after the HTTP codec and before `HttpStreamsServerHandler`, which merges the
  * [[HttpRequest]] / [[LastHttpContent]] messages this relies on into a single streamed request.
  *
  * A request is tracked until its response starts being written. Responses are written in request order, so the oldest tracked request is
  * the one awaiting a response. This holds also for responses written by other handlers, e.g. in custom pipelines.
  */
private[netty] class RequestBodyCompletionTracker extends ChannelDuplexHandler {

  private class BodyState(var fullyReceived: Boolean, var continueAwaited: Boolean)

  private val states = mutable.Queue.empty[BodyState]

  /** If true, the oldest request without a started response is still being received, and the client isn't waiting for a `100 Continue`; so
    * a timeout is the client's fault. False if no request is tracked.
    */
  def oldestRequestBodyStalled: Boolean = states.headOption.exists(s => !s.fullyReceived && !s.continueAwaited)

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    // body chunks concern the newest request, as a request's body is received before the next request
    msg match {
      // FullHttpRequest is both an HttpRequest and a LastHttpContent
      case r: HttpRequest     => states.enqueue(new BodyState(r.isInstanceOf[LastHttpContent], HttpUtil.is100ContinueExpected(r)))
      case _: LastHttpContent =>
        states.lastOption.foreach { s =>
          s.fullyReceived = true
          s.continueAwaited = false
        }
      case _: HttpContent => states.lastOption.foreach(_.continueAwaited = false) // the client sends the body anyway
      case _              => ()
    }
    val _ = ctx.fireChannelRead(msg)
  }

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = {
    msg match {
      // `HttpStreamsServerHandler` sends a 100 only for the newest request, while no newer one is received
      case r: HttpResponse if r.status() == HttpResponseStatus.CONTINUE => states.lastOption.foreach(_.continueAwaited = false)
      // other interim responses (but not 101, which is the final one before switching protocols) are followed by the final response
      case r: HttpResponse
          if r.status().codeClass() == HttpStatusClass.INFORMATIONAL && r.status() != HttpResponseStatus.SWITCHING_PROTOCOLS =>
        ()
      case _: HttpResponse => if (states.nonEmpty) { val _ = states.dequeue() }
      case _               => ()
    }
    val _ = ctx.write(msg, promise)
  }
}
