package sttp.tapir.server.vertx

import scala.concurrent.duration.FiniteDuration

import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.ws.WebSocketFrame

package object streams {

  def reactiveStreamsReadStreamCompatible(): ReadStreamCompatible[VertxStreams] = new ReadStreamCompatible[VertxStreams] {

    override val streams: VertxStreams = VertxStreams

    override def asReadStream(readStream: ReadStream[Buffer]): ReadStream[Buffer] =
      readStream

    override def fromReadStream(readStream: ReadStream[Buffer], maxBytes: Option[Long]): ReadStream[Buffer] = 
      maxBytes.map(new LimitedReadStream(readStream, _)).getOrElse(readStream)

    override def webSocketPipe[REQ, RESP](
        readStream: ReadStream[WebSocketFrame],
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, VertxStreams]
    ): ReadStream[WebSocketFrame] = {
      val stream0 = optionallyContatenateFrames(readStream, o.concatenateFragmentedFrames)
      val stream1 = optionallyIgnorePong(stream0, o.ignorePong)
      val stream2 = optionallyAutoPing(stream1, o.autoPing)

      val stream3 = new ReadStreamMapping(
        stream2,
        { (frame: WebSocketFrame) =>
          o.requests.decode(frame) match {
            case failure: DecodeResult.Failure =>
              throw (new WebSocketFrameDecodeFailure(frame, failure))
            case DecodeResult.Value(v) => v
          }
        }
      )

      new ReadStreamMapping(pipe(stream3), o.responses.encode)
    }
  }

  private def optionallyContatenateFrames(rs: ReadStream[WebSocketFrame], doConcatenate: Boolean): ReadStream[WebSocketFrame] = {
    // TODO implement this
    rs
  }

  private def optionallyIgnorePong(rs: ReadStream[WebSocketFrame], ignore: Boolean): ReadStream[WebSocketFrame] = {
    // TODO implement this
    rs
  }

  private def optionallyAutoPing(
      rs: ReadStream[WebSocketFrame],
      autoPing: Option[(FiniteDuration, WebSocketFrame.Ping)]
  ): ReadStream[WebSocketFrame] = {
    // TODO implement this
    rs
  }
}

/*
 * ReadStream doesn't offer a `map` function
 */
class ReadStreamMapping[A, B](source: ReadStream[A], mapping: A => B) extends ReadStream[B] {
  override def handler(handler: Handler[B]): ReadStream[B] = {
    if (handler == null) {
      source.handler(null)
    } else {
      source.handler(event => handler.handle(mapping.apply(event)))
    }
    this
  }
  override def exceptionHandler(handler: Handler[Throwable]): ReadStream[B] = {
    source.exceptionHandler(handler)
    this
  }
  override def pause(): ReadStream[B] = {
    source.pause()
    this
  }
  override def resume(): ReadStream[B] = {
    fetch(Long.MaxValue)
  }
  override def fetch(amount: Long): ReadStream[B] = {
    source.fetch(amount)
    this
  }
  override def endHandler(endHandler: Handler[Void]): ReadStream[B] = {
    source.endHandler(endHandler)
    this
  }
}
