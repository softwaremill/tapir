package sttp.tapir.server.vertx

import java.io.{ByteArrayInputStream, InputStream}

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{ServerWebSocket, WebSocketFrameType}
import io.vertx.core.streams.ReadStream
import sttp.ws.WebSocketFrame
import scala.annotation.tailrec
import java.util.concurrent.Callable

package object encoders {

  private val bufferSize = 1024

  /** README: Tests are using a ByteArrayInputStream, which is totally fine, but other blocking implementations like FileInputStream etc.
    * must maybe be wrapped in executeBlocking
    */
  private[vertx] def inputStreamToBuffer(is: InputStream, vertx: Vertx, byteLimit: Option[Long]): Future[Buffer] = {
    is match {
      case _: ByteArrayInputStream =>
        Future.succeededFuture(inputStreamToBufferUnsafe(is, byteLimit))
      case _ =>
        vertx.executeBlocking(new Callable[Buffer] { override def call(): Buffer = inputStreamToBufferUnsafe(is, byteLimit) })
    }
  }

  private def inputStreamToBufferUnsafe(is: InputStream, byteLimit: Option[Long]): Buffer = {
    val buffer = Buffer.buffer()

    @tailrec
    def readRec(buffer: Buffer, readSoFar: Long): Buffer =
      if (byteLimit.exists(_ <= readSoFar) || is.available() <= 0)
        buffer
      else {
        val bytes = is.readNBytes(bufferSize)
        val length = bytes.length.toLong
        val lengthToWrite: Int = byteLimit.map(limit => Math.min(limit - readSoFar, length)).getOrElse(length).toInt
        readRec(buffer.appendBytes(bytes, 0, lengthToWrite), readSoFar = readSoFar + lengthToWrite)
      }

    readRec(buffer, readSoFar = 0L)
  }

  def wrapWebSocket(websocket: ServerWebSocket): ReadStream[WebSocketFrame] =
    new ReadStream[WebSocketFrame] {
      override def exceptionHandler(handler: Handler[Throwable]): ReadStream[WebSocketFrame] = {
        websocket.exceptionHandler(handler)
        this
      }

      override def handler(handler: Handler[WebSocketFrame]): ReadStream[WebSocketFrame] = {
        websocket.frameHandler { frame =>
          val t = frame.`type`()
          if (t == WebSocketFrameType.TEXT) {
            handler.handle(WebSocketFrame.Text(frame.textData(), frame.isFinal(), None))
          } else if (t == WebSocketFrameType.BINARY) {
            handler.handle(WebSocketFrame.Binary(frame.binaryData().getBytes(), frame.isFinal(), None))
          } else if (t == WebSocketFrameType.CLOSE) {
            handler.handle(WebSocketFrame.Close(frame.closeStatusCode(), frame.closeReason()))
          } else if (t == WebSocketFrameType.PING) {
            handler.handle(WebSocketFrame.Ping(frame.binaryData().getBytes()))
          } else if (t == WebSocketFrameType.PONG) {
            handler.handle(WebSocketFrame.Pong(frame.binaryData().getBytes()))
          } else if (t == WebSocketFrameType.CONTINUATION) {
            handler.handle(WebSocketFrame.Binary(frame.binaryData().getBytes(), frame.isFinal(), None))
          }
        }
        websocket.pongHandler { buffer =>
          handler.handle(WebSocketFrame.Pong(buffer.getBytes()))
        }
        this
      }

      override def pause(): ReadStream[WebSocketFrame] = {
        websocket.pause()
        this
      }

      override def resume(): ReadStream[WebSocketFrame] = {
        websocket.resume()
        this
      }

      override def fetch(amount: Long): ReadStream[WebSocketFrame] = {
        websocket.fetch(amount)
        this
      }

      override def endHandler(endHandler: Handler[Void]): ReadStream[WebSocketFrame] = {
        websocket.endHandler(endHandler)
        this
      }
    }
}
