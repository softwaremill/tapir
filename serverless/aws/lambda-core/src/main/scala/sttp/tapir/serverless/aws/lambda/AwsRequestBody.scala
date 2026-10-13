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
import java.nio.charset.Charset
import java.util.Base64

private[lambda] class AwsRequestBody[F[_]: MonadError]() extends RequestBody[F, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]] = {
    val request = awsRequest(serverRequest)
    val decoded =
      if (request.isBase64Encoded) Left(Base64.getDecoder.decode(request.body.getOrElse(""))) else Right(request.body.getOrElse(""))

    def asByteArray: Array[Byte] = decoded.fold(identity[Array[Byte]], _.getBytes())

    RawValue(bodyType match {
      case RawBodyType.StringBody(charset)  => decoded.fold(new String(_, charset), identity[String])
      case RawBodyType.ByteArrayBody        => asByteArray
      case RawBodyType.ByteBufferBody       => ByteBuffer.wrap(asByteArray)
      case RawBodyType.InputStreamBody      => new ByteArrayInputStream(asByteArray)
      case RawBodyType.InputStreamRangeBody => InputStreamRange(() => new ByteArrayInputStream(asByteArray))
      case RawBodyType.FileBody             => throw new UnsupportedOperationException
      case _: RawBodyType.MultipartBody     => throw new UnsupportedOperationException
    }).asInstanceOf[RawValue[R]].unit
  }

  // A non-base64 body is already a String, which toRaw returns as is; asByteArray encodes it with the default charset
  override def stringBodyCharset(serverRequest: ServerRequest, default: Charset): Charset =
    if (awsRequest(serverRequest).isBase64Encoded) default else Charset.defaultCharset()

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException

  private def awsRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[AwsRequest]
}
