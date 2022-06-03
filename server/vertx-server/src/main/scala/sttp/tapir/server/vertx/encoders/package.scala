package sttp.tapir.server.vertx

import java.io.{ByteArrayInputStream, InputStream}

import io.vertx.core.buffer.Buffer
import io.vertx.core.Future
import io.vertx.core.Vertx

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

}
