package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.core.Handler

class FakeStream extends ReadStream[Buffer] { self =>

  private var demand = Long.MaxValue
  private var eventHandler: Handler[Buffer] = _
  private var endHandler: Handler[Void] = _
  private var exceptionHandler: Handler[Throwable] = _
  @volatile var pauseCount: Int = 0
  @volatile var resumeCount: Int = 0

  val lock = new Object()

  override def exceptionHandler(handler: Handler[Throwable]): ReadStream[Buffer] = {
    exceptionHandler = handler
    self
  }

  override def handler(handler: Handler[Buffer]): ReadStream[Buffer] = {
    eventHandler = handler
    self
  }

  override def fetch(amount: Long): ReadStream[Buffer] =
    lock.synchronized {
      demand += amount
      if (demand < 0L) {
        demand = Long.MaxValue
      }
      lock.notifyAll()
      self
    }

  override def pause(): ReadStream[Buffer] = {
    demand = 0L
    pauseCount += 1

    self
  }

  override def resume(): ReadStream[Buffer] = {
    resumeCount += 1
    fetch(Long.MaxValue)
  }

  override def endHandler(handler: Handler[Void]): ReadStream[Buffer] = {
    endHandler = handler
    self
  }

  def isPaused(): Boolean =
    demand == 0L

  def handle(s: String): Unit =
    handle(Buffer.buffer(s))

  def handle(buff: Buffer): Unit =
    lock.synchronized {
      if (demand == 0L) lock.wait()
      if (demand != Long.MaxValue) demand -= 1
      eventHandler.handle(buff)
    }

  def fail(err: Throwable): Unit =
    exceptionHandler.handle(err)

  def end(): Unit =
    endHandler.handle(null)
}
