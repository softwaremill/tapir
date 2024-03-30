package sttp.tapir.server.vertx.streams

import io.vertx.core.streams.ReadStream
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import sttp.capabilities.StreamMaxLengthExceededException

/** An adapter for Vertx ReadStream[Buffer], which passes bytes through, but fails with a
  * [[sttp.capabilities.StreamMaxLengthExceededException]] if exceeds given limit. This exception should be handled by
  * [[sttp.tapir.server.interceptor.exception.DefaultExceptionHandler]] in order to return a HTTP 413 Payload Too Large.
  */
private[vertx] class LimitedReadStream(source: ReadStream[Buffer], maxBytes: Long) extends ReadStream[Buffer] {

  // Safe, Vertx uses a single thread
  private var bytesReadSoFar: Long = 0
  private var endHandler: Handler[Void] = _
  private var exceptionHandler: Handler[Throwable] = _
  private var dataHandler: Handler[Buffer] = _

  override def handler(handler: Handler[Buffer]): ReadStream[Buffer] = {
    dataHandler = (buffer: Buffer) => {
      bytesReadSoFar += buffer.length()
      if (bytesReadSoFar > maxBytes) {
        if (exceptionHandler != null) {
          exceptionHandler.handle(new StreamMaxLengthExceededException(maxBytes))
        }
      } else {
        handler.handle(buffer)
      }
    }
    source.handler(dataHandler)
    this
  }

  override def exceptionHandler(handler: Handler[Throwable]): ReadStream[Buffer] = {
    this.exceptionHandler = handler
    source.exceptionHandler(handler)
    this
  }
  override def pause(): ReadStream[Buffer] = {
    source.pause()
    this
  }
  override def resume(): ReadStream[Buffer] = {
    fetch(Long.MaxValue)
  }
  override def fetch(amount: Long): ReadStream[Buffer] = {
    source.fetch(amount)
    this
  }
  override def endHandler(endHandler: Handler[Void]): ReadStream[Buffer] = {
    this.endHandler = endHandler
    source.endHandler(endHandler)
    this
  }
}
