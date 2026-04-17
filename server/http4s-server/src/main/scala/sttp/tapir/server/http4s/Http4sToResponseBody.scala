package sttp.tapir.server.http4s

import cats.effect.{Async, Sync}
import cats.syntax.all._
import fs2.io.file.Files
import fs2.{Chunk, Stream}
import org.http4s
import org.http4s.Header.ToRaw.rawToRaw
import org.http4s._
import org.http4s.headers.{`Content-Disposition`, `Content-Length`, `Content-Type`}
import org.typelevel.ci.CIString
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{HasHeaders, HeaderNames, Part}
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, RawBodyType, RawPart, WebSocketBodyOutput}

import java.io.InputStream
import java.nio.charset.Charset

private[http4s] class Http4sToResponseBody[F[_]: Async](
    serverOptions: Http4sServerOptions[F]
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
    Right((v, None))

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
  ): Http4sResponseBody[F] = Left(Http4sWebSockets.pipeToBody(pipe, o))

  private def rawValueToEntity[CF <: CodecFormat, R](bodyType: RawBodyType[R], r: R): (EntityBody[F], Option[Long]) = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = r.toString.getBytes(charset)
        (fs2.Stream.chunk(Chunk.array(bytes)), Some(bytes.length))
      case RawBodyType.ByteArrayBody        => (fs2.Stream.chunk(Chunk.array(r)), Some((r: Array[Byte]).length))
      case RawBodyType.ByteBufferBody       => (fs2.Stream.chunk(Chunk.byteBuffer(r)), None)
      case RawBodyType.InputStreamBody      => (inputStreamToFs2(() => r), None)
      case RawBodyType.InputStreamRangeBody =>
        val fs2Stream = r.range
          .map(range => inputStreamToFs2(r.inputStreamFromRangeStart).take(range.contentLength))
          .getOrElse(inputStreamToFs2(r.inputStream))
        (fs2Stream, None)
      case RawBodyType.FileBody =>
        val tapirFile = r
        val stream = tapirFile.range
          .flatMap(r => r.startAndEnd.map(s => Files[F].readRange(tapirFile.file.toPath, r.contentLength.toInt, s._1, s._2)))
          .getOrElse(Files[F].readAll(tapirFile.file.toPath, serverOptions.ioChunkSize))
        (stream, Some(tapirFile.file.length))
      case m: RawBodyType.MultipartBody =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(m, _))
        val body = implicitly[EntityEncoder[F, multipart.Multipart[F]]].toEntity(multipart.Multipart(parts.toVector)).body
        (body, None)
    }
  }

  private def inputStreamToFs2(inputStream: () => InputStream) =
    fs2.io.readInputStream(
      Sync[F].blocking(inputStream()),
      serverOptions.ioChunkSize
    )

  private def rawPartToBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T]): Option[multipart.Part[F]] = {
    m.partType(part.name).map { partType =>
      val headers: List[Header.ToRaw] = part.headers.map { header =>
        rawToRaw(Header.Raw(CIString(header.name), header.value))
      }.toList

      val partContentType =
        part.contentType.map(parseContentType).getOrElse(`Content-Type`(http4s.MediaType.application.`octet-stream`))
      val (entity, contentLength) = rawValueToEntity(partType.asInstanceOf[RawBodyType[Any]], part.body)

      val dispositionParams = (part.otherDispositionParams + (Part.NameDispositionParam -> part.name)).map { case (k, v) =>
        CIString(k) -> v
      }
      val contentDispositionHeader: Header.ToRaw = `Content-Disposition`("form-data", dispositionParams)

      val shouldAddCtHeader = part.headers.exists(_.is(HeaderNames.ContentType))
      val allHeaders0 = if (shouldAddCtHeader) {
        Headers.apply((partContentType: Header.ToRaw) :: contentDispositionHeader :: headers)
      } else {
        Headers(contentDispositionHeader :: headers)
      }

      val shouldAddClHeader = part.headers.exists(_.is(HeaderNames.ContentLength))
      val allHeaders = contentLength match {
        case Some(cl) if shouldAddClHeader => allHeaders0.put(`Content-Length`(cl))
        case _                             => allHeaders0
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
