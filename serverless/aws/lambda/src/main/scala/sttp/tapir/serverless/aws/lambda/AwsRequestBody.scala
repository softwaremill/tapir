package sttp.tapir.serverless.aws.lambda

import sttp.capabilities
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.RawBodyType
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.Base64

private[lambda] class AwsRequestBody[F[_]: MonadError](request: AwsRequest) extends RequestBody[F, Nothing] {
  override val streams: capabilities.Streams[Nothing] = NoStreams

  override def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]] = {
    val decoded =
      if (request.isBase64Encoded) Left(Base64.getDecoder.decode(request.body.getOrElse(""))) else Right(request.body.getOrElse(""))

    def asByteArray: Array[Byte] = decoded.fold(identity[Array[Byte]], _.getBytes())

    RawValue(bodyType match {
      case RawBodyType.StringBody(charset) => decoded.fold(new String(_, charset), identity[String])
      case RawBodyType.ByteArrayBody       => asByteArray
      case RawBodyType.ByteBufferBody      => ByteBuffer.wrap(asByteArray)
      case RawBodyType.InputStreamBody     => new ByteArrayInputStream(asByteArray)
      case RawBodyType.FileBody            => throw new UnsupportedOperationException
      case _: RawBodyType.MultipartBody    => throw new UnsupportedOperationException
    }).asInstanceOf[RawValue[R]].unit
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException
}
