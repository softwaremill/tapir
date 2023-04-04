package sttp.tapir.server.vertx

import java.io.{ByteArrayInputStream, InputStream}

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{ServerWebSocket, WebSocketFrameType}
import io.vertx.core.streams.ReadStream
import sttp.ws.WebSocketFrame

package object encoders {

  private val bufferSize = 1024

  /** README: Tests are using a ByteArrayInputStream, which is totally fine, but other blocking implementations like FileInputStream etc.
    * must maybe be wrapped in executeBlocking
    */
  private[vertx] def inputStreamToBuffer(is: InputStream, vertx: Vertx): Future[Buffer] = {
    is match {
      case _: ByteArrayInputStream =>
        Future.succeededFuture(inputStreamToBufferUnsafe(is))
      case _ =>
        vertx.executeBlocking { promise => promise.complete(inputStreamToBufferUnsafe(is)) }
    }
  }

  private def inputStreamToBufferUnsafe(is: InputStream): Buffer = {
    val buffer = Buffer.buffer()
    val buf = new Array[Byte](bufferSize)
    while (is.available() > 0) {
      val read = is.read(buf)
      buffer.appendBytes(buf, 0, read)
    }
    buffer
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
