package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.{InputStreamRange, RawBodyType}

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.util.Try

/** Reads a bytes-like request body from `delegate` at most once, buffering the bytes so that subsequent reads - e.g. a secondary body
  * decoded during the security phase, followed by the endpoint's own body - are served from memory.
  *
  * Must be created per request: it holds that request's bytes.
  */
private[tapir] class CachingRequestBody[F[_], S](delegate: RequestBody[F, S])(implicit m: MonadError[F]) extends RequestBody[F, S] {

  override val streams: Streams[S] = delegate.streams

  private var cachedBytes: Option[Array[Byte]] = None

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]] =
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        val charset = requestCharset(serverRequest).getOrElse(defaultCharset)
        bytes(serverRequest, maxBytes).map(bs => RawValue(new String(bs, charset)))
      case RawBodyType.ByteArrayBody =>
        // identity codec: without the clone, a caller mutating the array would corrupt the cache
        bytes(serverRequest, maxBytes).map(bs => RawValue(bs.clone()))
      case RawBodyType.ByteBufferBody =>
        // clone as above; wrap rather than asReadOnlyBuffer so .array() keeps working
        bytes(serverRequest, maxBytes).map(bs => RawValue(ByteBuffer.wrap(bs.clone())))
      case RawBodyType.InputStreamBody =>
        bytes(serverRequest, maxBytes).map(bs => RawValue(new ByteArrayInputStream(bs): InputStream))
      case RawBodyType.InputStreamRangeBody =>
        bytes(serverRequest, maxBytes)
          .map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))))
      // never cached: EndpointBodyVerifier rejects these alongside a secondary body
      case RawBodyType.FileBody | _: RawBodyType.MultipartBody => delegate.toRaw(serverRequest, bodyType, maxBytes)
    }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    delegate.toStream(serverRequest, maxBytes).asInstanceOf[streams.BinaryStream]

  private def requestCharset(serverRequest: ServerRequest): Option[Charset] =
    serverRequest.contentTypeParsed.flatMap(_.charset).flatMap(cs => Try(Charset.forName(cs)).toOption)

  // only the first call's maxBytes applies; both phases derive it from the same EndpointInfo, so they agree
  private def bytes(serverRequest: ServerRequest, maxBytes: Option[Long]): F[Array[Byte]] =
    cachedBytes match {
      case Some(bs) => bs.unit
      case None     =>
        delegate.toRaw(serverRequest, RawBodyType.ByteArrayBody, maxBytes).map { raw =>
          cachedBytes = Some(raw.value)
          raw.value
        }
    }
}
