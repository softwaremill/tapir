package sttp.tapir.serverless.aws.lambda

import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Base64

private[lambda] object AwsToResponseBody extends ToResponseBody[String, Nothing] {
  override val streams: capabilities.Streams[Nothing] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): String = bodyType match {
    case RawBodyType.StringBody(charset) =>
      val str = v.asInstanceOf[String]
      Base64.getEncoder.encodeToString(str.getBytes(charset))

    case RawBodyType.ByteArrayBody =>
      val bytes = v.asInstanceOf[Array[Byte]]
      Base64.getEncoder.encodeToString(bytes)

    case RawBodyType.ByteBufferBody =>
      val byteBuffer = v.asInstanceOf[ByteBuffer]
      Base64.getEncoder.encodeToString(safeRead(byteBuffer))

    case RawBodyType.InputStreamBody =>
      val stream = v.asInstanceOf[InputStream]
      Base64.getEncoder.encodeToString(stream.readAllBytes())

    case RawBodyType.FileBody => throw new UnsupportedOperationException

    case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException
  }

  private def safeRead(byteBuffer: ByteBuffer): Array[Byte] = {
    if (byteBuffer.hasArray) {
      byteBuffer.array()
    } else {
      val array = new Array[Byte](byteBuffer.remaining())
      byteBuffer.get(array)
      array
    }
  }

  override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): String =
    throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
  ): String = throw new UnsupportedOperationException
}
