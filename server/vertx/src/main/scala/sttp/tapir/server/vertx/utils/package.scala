package sttp.tapir.server.vertx

import java.io.InputStream

import io.vertx.core.buffer.Buffer

package object utils {
  /**
   * README: Tests are using a ByteArrayInputStream, which is totally fine,
   * but other blocking implementations like FileInputStream etc. should maybe be wrapped in executeBlocking:
   * it's fine for encoding responses, but will not be suitable decoding requests (which expect an InputStream...)
   */
  def inputStreamToBuffer(is: InputStream): Buffer = {
    val buffer = Buffer.buffer()
    while (is.available() > 0) {
      buffer.appendBytes(is.readNBytes(1024))
    }
    buffer
  }

}
