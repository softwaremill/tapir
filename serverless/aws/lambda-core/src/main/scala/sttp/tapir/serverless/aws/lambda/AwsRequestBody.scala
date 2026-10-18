package sttp.tapir.serverless.aws.lambda

import sttp.capabilities
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{InputStreamRange, RawBodyType}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

private[lambda] class AwsRequestBody[F[_]: MonadError]() extends RequestBody[F, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]] = {
    val request = awsRequest(serverRequest)
    val body = request.body.getOrElse("")
    // a non-base64 body is text, which the Lambda event carries as UTF-8
    def asByteArray: Array[Byte] = if (request.isBase64Encoded) Base64.getDecoder.decode(body) else body.getBytes(StandardCharsets.UTF_8)

    RawValue(bodyType match {
      case RawBodyType.StringBody(charset)  => new String(asByteArray, RequestBody.requestCharset(serverRequest).getOrElse(charset))
      case RawBodyType.ByteArrayBody        => asByteArray
      case RawBodyType.ByteBufferBody       => ByteBuffer.wrap(asByteArray)
      case RawBodyType.InputStreamBody      => new ByteArrayInputStream(asByteArray)
      case RawBodyType.InputStreamRangeBody => InputStreamRange(() => new ByteArrayInputStream(asByteArray))
      case RawBodyType.FileBody             => throw new UnsupportedOperationException
      case _: RawBodyType.MultipartBody     => throw new UnsupportedOperationException
    }).asInstanceOf[RawValue[R]].unit
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException

  private def awsRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[AwsRequest]
}
