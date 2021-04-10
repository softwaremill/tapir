package sttp.tapir.server.http4s

import cats.effect.Concurrent
import cats.syntax.all._
import fs2.{Chunk, Stream}
import org.http4s
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.{EntityBody, EntityEncoder, Header, Headers, multipart}
import org.http4s.util.CaseInsensitiveString
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{HasHeaders, Part, Header => SttpHeader}
import sttp.tapir.{CodecFormat, RawBodyType, RawPart, WebSocketBodyOutput}
import sttp.tapir.server.interpreter.ToResponseBody

import java.nio.charset.Charset
import cats.effect.Temporal

private[http4s] class Http4sToResponseBody[F[_]: Concurrent: Temporal: ContextShift, G[_]](
    serverOptions: Http4sServerOptions[F, G]
) extends ToResponseBody[Http4sResponseBody[F], Fs2Streams[F]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): Http4sResponseBody[F] =
    Right(rawValueToEntity(bodyType, v))

  override def fromStreamValue(
      v: Stream[F, Byte],
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): Http4sResponseBody[F] =
    Right(v)

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
  ): Http4sResponseBody[F] = Left(Http4sWebSockets.pipeToBody(pipe, o))

  private def rawValueToEntity[CF <: CodecFormat, R](bodyType: RawBodyType[R], r: R): EntityBody[F] = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = r.toString.getBytes(charset)
        fs2.Stream.chunk(Chunk.bytes(bytes))
      case RawBodyType.ByteArrayBody  => fs2.Stream.chunk(Chunk.bytes(r))
      case RawBodyType.ByteBufferBody => fs2.Stream.chunk(Chunk.byteBuffer(r))
      case RawBodyType.InputStreamBody =>
        fs2.io.readInputStream(
          r.pure[F],
          serverOptions.ioChunkSize,
          Blocker.liftExecutionContext(serverOptions.blockingExecutionContext)
        )
      case RawBodyType.FileBody =>
        fs2.io.file.readAll[F](r.toPath, Blocker.liftExecutionContext(serverOptions.blockingExecutionContext), serverOptions.ioChunkSize)
      case m: RawBodyType.MultipartBody =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(m, _))
        val body = implicitly[EntityEncoder[F, multipart.Multipart[F]]].toEntity(multipart.Multipart(parts.toVector)).body
        body
    }
  }

  private def rawPartToBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T]): Option[multipart.Part[F]] = {
    m.partType(part.name).map { partType =>
      val headers = part.headers.map { case SttpHeader(hk, hv) =>
        Header.Raw(CaseInsensitiveString(hk), hv)
      }.toList

      val partContentType = part.contentType.map(parseContentType).getOrElse(`Content-Type`(http4s.MediaType.application.`octet-stream`))
      val entity = rawValueToEntity(partType.asInstanceOf[RawBodyType[Any]], part.body)

      val dispositionParams = part.otherDispositionParams + (Part.NameDispositionParam -> part.name)
      val contentDispositionHeader = `Content-Disposition`("form-data", dispositionParams)

      val shouldAddCtHeader = headers.exists(_.name == `Content-Type`.name)
      val allHeaders = if (shouldAddCtHeader) {
        Headers(partContentType :: contentDispositionHeader :: headers)
      } else {
        Headers(contentDispositionHeader :: headers)
      }

      multipart.Part(allHeaders, entity)
    }
  }

  private def parseContentType(ct: String): `Content-Type` =
    `Content-Type`(
      http4s.MediaType
        .parse(ct)
        .getOrElse(throw new IllegalArgumentException(s"Cannot parse content type: $ct"))
    )
}
