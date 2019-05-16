package tapir.server.finatra
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.twitter.finagle.http.Request
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawValueType,
  StringValueType
}

object FinatraRequestToRawBody {
  def apply[R](rawBodyType: RawValueType[R], request: Request): R = {

    val body = request.content

    def asByteArray: Array[Byte] = {
      val array = new Array[Byte](request.content.length)
      request.content.write(array, 0)
      array
    }

    def asByteBuffer: ByteBuffer = {
      val buffer = ByteBuffer.allocate(request.content.length)
      request.content.write(buffer)
      buffer.flip()
      buffer
    }

    rawBodyType match {
      case StringValueType(defaultCharset) => new String(asByteArray, request.charset.map(Charset.forName).getOrElse(defaultCharset))
      case ByteArrayValueType              => asByteArray
      case ByteBufferValueType             => asByteBuffer
      case InputStreamValueType            => new ByteArrayInputStream(asByteArray)
      case FileValueType =>
        ???
      case _: MultipartValueType =>
        ???
    }
  }
}
