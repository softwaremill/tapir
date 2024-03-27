package sttp.tapir.serverless.aws.lambda

import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.Charset
import java.util.Base64

private[lambda] class AwsToResponseBody[F[_]](options: AwsServerOptions[F]) extends ToResponseBody[LambdaResponseBody, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): LambdaResponseBody =
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val str = v.asInstanceOf[String]
        val r = if (options.encodeResponseBody) Base64.getEncoder.encodeToString(str.getBytes(charset)) else str(r, Some(str.length.toLong))

      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        val r = if (options.encodeResponseBody) Base64.getEncoder.encodeToString(bytes) else new String(bytes)
        (r, Some(bytes.length.toLong))

      case RawBodyType.ByteBufferBody       => throw new UnsupportedOperationException
      case RawBodyType.InputStreamBody      => throw new UnsupportedOperationException
      case RawBodyType.InputStreamRangeBody => throw new UnsupportedOperationException
      case RawBodyType.FileBody             => throw new UnsupportedOperationException
      case _: RawBodyType.MultipartBody     => throw new UnsupportedOperationException
    }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): LambdaResponseBody =
    throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): LambdaResponseBody = throw new UnsupportedOperationException
}
