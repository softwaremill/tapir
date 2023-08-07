package sttp.tapir.server.jdkhttp
package internal

import com.sun.net.httpserver.HttpExchange
import sttp.capabilities
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.{Files, StandardCopyOption}

private[jdkhttp] class JdkHttpRequestBody(createFile: ServerRequest => TapirFile) extends RequestBody[Id, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW]): RawValue[RAW] = {
    def asInputStream: InputStream = jdkHttpRequest(serverRequest).getRequestBody
    def asByteArray: Array[Byte] = asInputStream.readAllBytes()

    bodyType match {
      case RawBodyType.InputStreamRangeBody => RawValue(InputStreamRange(() => asInputStream))
      case RawBodyType.StringBody(charset)  => RawValue(new String(asByteArray, charset))
      case RawBodyType.ByteArrayBody        => RawValue(asByteArray)
      case RawBodyType.ByteBufferBody       => RawValue(ByteBuffer.wrap(asByteArray))
      case RawBodyType.InputStreamBody      => RawValue(asInputStream)
      case RawBodyType.FileBody =>
        val file = createFile(serverRequest)
        Files.copy(asInputStream, file.toPath, StandardCopyOption.REPLACE_EXISTING)
        RawValue(FileRange(file), Seq(FileRange(file)))
      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException("MultipartBody is not supported")
    }
  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = throw new UnsupportedOperationException(
    "Streaming is not supported"
  )

  private def jdkHttpRequest(serverRequest: ServerRequest): HttpExchange =
    serverRequest.underlying.asInstanceOf[HttpExchange]
}
