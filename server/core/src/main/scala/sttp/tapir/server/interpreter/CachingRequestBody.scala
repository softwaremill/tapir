package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.{InputStreamRange, RawBodyType}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/** Reads a bytes-like request body from `delegate` at most once, buffering the bytes so that subsequent reads - e.g.
  * an extracted body decoded during the security phase, followed by the endpoint's own body - are served from
  * memory.
  *
  * Must be created per request: it holds that request's bytes.
  */
private[tapir] class CachingRequestBody[F[_], S](delegate: RequestBody[F, S])(implicit m: MonadError[F])
    extends RequestBody[F, S] {

  override val streams: Streams[S] = delegate.streams

  // A plain var needs no synchronisation here: the interpreter's flatMap chain reads the security-phase body strictly
  // before the main-phase one, and this instance never outlives a single request.
  private var cachedBytes: Option[Array[Byte]] = None

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]] =
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        bytes(serverRequest, maxBytes).map(bs => RawValue(new String(bs, charset)).asInstanceOf[RawValue[R]])
      case RawBodyType.ByteArrayBody =>
        // clone: byteArrayBody is an identity codec, so the caller receives this array as-is and could mutate it
        // in place, corrupting the cache for the next read
        bytes(serverRequest, maxBytes).map(bs => RawValue(bs.clone()).asInstanceOf[RawValue[R]])
      case RawBodyType.ByteBufferBody =>
        // clone for the same reason as ByteArrayBody above; wrap (not asReadOnlyBuffer) so .array() keeps working
        bytes(serverRequest, maxBytes).map(bs => RawValue(ByteBuffer.wrap(bs.clone())).asInstanceOf[RawValue[R]])
      case RawBodyType.InputStreamBody =>
        bytes(serverRequest, maxBytes).map(bs => RawValue(new ByteArrayInputStream(bs)).asInstanceOf[RawValue[R]])
      case RawBodyType.InputStreamRangeBody =>
        bytes(serverRequest, maxBytes)
          .map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))).asInstanceOf[RawValue[R]])
      // File and multipart bodies are never served from the cache. An endpoint combining one of them with an
      // extracted body is rejected by EndpointVerifier at route construction, so this branch only ever sees an
      // endpoint whose sole body is the primary one.
      case other => delegate.toRaw(serverRequest, other, maxBytes)
    }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    delegate.toStream(serverRequest, maxBytes).asInstanceOf[streams.BinaryStream]

  private def bytes(serverRequest: ServerRequest, maxBytes: Option[Long]): F[Array[Byte]] =
    cachedBytes match {
      case Some(bs) => bs.unit
      case None =>
        delegate.toRaw(serverRequest, RawBodyType.ByteArrayBody, maxBytes).map { raw =>
          cachedBytes = Some(raw.value)
          raw.value
        }
    }
}
