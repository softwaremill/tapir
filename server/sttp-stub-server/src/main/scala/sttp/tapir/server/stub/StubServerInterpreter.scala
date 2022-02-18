package sttp.tapir.server.stub

import sttp.client3.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  Response,
  StreamBody,
  StringBody
}
import sttp.model.{RequestMetadata, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.RawBodyType
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{Interceptor, RequestResult}
import sttp.tapir.server.interpreter._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.nio.ByteBuffer
import scala.annotation.tailrec
import scala.util.{Success, Try}

private[stub] object StubServerInterpreter {
  def apply[F[_]: MonadError, R, T](
      req: Request[T, R],
      endpoints: List[ServerEndpoint[R, F]],
      interceptors: List[Interceptor[F]]
  ): F[Response[_]] = {

    implicit val bodyListener: BodyListener[F, Any] = new BodyListener[F, Any] {
      override def onComplete(body: Any)(cb: Try[Unit] => F[Unit]): F[Any] = cb(Success(())).map(_ => body)
    }

    val interpreter =
      new ServerInterpreter[R, F, Any, AnyStreams](endpoints, SttpResponseEncoder.toResponseBody, interceptors, _ => ().unit)

    val sRequest = new SttpRequest(req)

    val body: Either[Array[Byte], Any] = req.body match {
      case NoBody                     => Left(Array.emptyByteArray)
      case StringBody(s, encoding, _) => Left(s.getBytes(encoding))
      case ByteArrayBody(b, _)        => Left(b)
      case ByteBufferBody(b, _)       => Left(b.array())
      case InputStreamBody(b, _)      => Left(toByteArray(b))
      case FileBody(f, _)             => Left(f.readAsByteArray)
      case StreamBody(s)              => Right(s)
      case MultipartBody(_) =>
        throw new IllegalArgumentException("Stub cannot handle multipart bodies")
    }

    interpreter.apply(sRequest, requestBody[F](body)).map {
      case RequestResult.Response(sResponse) => toResponse(sRequest, sResponse)
      case RequestResult.Failure(_)          => toResponse(sRequest, ServerResponse(StatusCode.NotFound, Nil, None))
    }
  }

  private val toResponse: (ServerRequest, ServerResponse[Any]) => Response[Any] = (sRequest, sResponse) => {
    val metadata = RequestMetadata(sRequest.method, sRequest.uri, sRequest.headers)
    sttp.client3.Response(sResponse.body.getOrElse(()), sResponse.code, "", sResponse.headers, Nil, metadata)
  }

  private def toByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    var read = 0
    val buf = new Array[Byte](1024)

    @tailrec
    def transfer(): Unit = {
      read = is.read(buf, 0, buf.length)
      if (read != -1) {
        os.write(buf, 0, read)
        transfer()
      }
    }

    transfer()
    os.toByteArray
  }

  private def requestBody[F[_]](body: Either[Array[Byte], Any])(implicit ME: MonadError[F]): RequestBody[F, AnyStreams] =
    new RequestBody[F, AnyStreams] {
      override val streams: AnyStreams = AnyStreams
      override def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]] =
        body match {
          case Left(bytes) =>
            bodyType match {
              case RawBodyType.StringBody(charset) => ME.unit(RawValue(new String(bytes, charset)))
              case RawBodyType.ByteArrayBody       => ME.unit(RawValue(bytes))
              case RawBodyType.ByteBufferBody      => ME.unit(RawValue(ByteBuffer.wrap(bytes)))
              case RawBodyType.InputStreamBody     => ME.unit(RawValue(new ByteArrayInputStream(bytes)))
              case RawBodyType.FileBody            => ME.error(new UnsupportedOperationException)
              case _: RawBodyType.MultipartBody    => ME.error(new UnsupportedOperationException)
            }
          case _ => throw new IllegalArgumentException("Raw body type provided while endpoint accepts stream body")
        }
      override def toStream(): streams.BinaryStream = body match {
        case Right(stream) => ME.unit(RawValue(stream))
        case _             => throw new IllegalArgumentException("Stream body provided while endpoint accepts raw body types")
      }
    }
}
