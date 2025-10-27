package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.scaladsl.{FileIO, StreamConverters}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.{HasHeaders, HeaderNames, Part}
import sttp.tapir.internal.charset
import sttp.tapir.server.pekkohttp.PekkoModel.parseHeadersOrThrowWithoutContentHeaders
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, RawPart, WebSocketBodyOutput}
import java.nio.charset.{Charset, StandardCharsets}
import scala.concurrent.ExecutionContext
import scala.util.Try

private[pekkohttp] class PekkoToResponseBody(implicit m: Materializer, ec: ExecutionContext)
    extends ToResponseBody[PekkoResponseBody, PekkoStreams] {
  override val streams: PekkoStreams = PekkoStreams
  private val ChunkSize = 8192

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): PekkoResponseBody =
    Right(
      overrideContentTypeIfDefined(
        rawValueToResponseEntity(bodyType, formatToContentType(format, charset(bodyType)), headers.contentLength, v),
        headers
      )
    )

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): PekkoResponseBody =
    Right(overrideContentTypeIfDefined(streamToEntity(formatToContentType(format, charset), headers.contentLength, v), headers))

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, PekkoStreams]
  ): PekkoResponseBody = Left(PekkoWebSockets.pipeToBody(pipe, o))

  private def rawValueToResponseEntity[CF <: CodecFormat, R](
      bodyType: RawBodyType[R],
      ct: ContentType,
      contentLength: Option[Long],
      r: R
  ): ResponseEntity = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        ct match {
          case nb: ContentType.NonBinary => HttpEntity(nb, r)
          case _                         => HttpEntity(ct, r.getBytes(charset))
        }
      case RawBodyType.ByteArrayBody        => HttpEntity(ct, r)
      case RawBodyType.ByteBufferBody       => HttpEntity(ct, ByteString(r))
      case RawBodyType.InputStreamBody      => streamToEntity(ct, contentLength, StreamConverters.fromInputStream(() => r, ChunkSize))
      case RawBodyType.InputStreamRangeBody =>
        val resource = r
        val initialStream = StreamConverters.fromInputStream(resource.inputStreamFromRangeStart, ChunkSize)
        resource.range
          .map(r => streamToEntity(ct, contentLength, toRangedStream(initialStream, bytesTotal = r.contentLength)))
          .getOrElse(streamToEntity(ct, contentLength, initialStream))
      case RawBodyType.FileBody =>
        val tapirFile = r
        tapirFile.range
          .flatMap(r => r.startAndEnd.map(s => HttpEntity(ct, createFileSource(tapirFile, s._1, r.contentLength))))
          .getOrElse(HttpEntity.fromPath(ct, tapirFile.file.toPath))
      case m: RawBodyType.MultipartBody =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(m, _))
        val body = Multipart.FormData(parts: _*)
        body.toEntity
    }
  }

  private def createFileSource(
      tapirFile: FileRange,
      start: Long,
      bytesTotal: Long
  ): PekkoStreams.BinaryStream =
    toRangedStream(FileIO.fromPath(tapirFile.file.toPath, ChunkSize, startPosition = start), bytesTotal)

  private def toRangedStream(initialStream: PekkoStreams.BinaryStream, bytesTotal: Long): PekkoStreams.BinaryStream =
    initialStream
      .scan((0L, ByteString.empty)) { case ((bytesConsumed, _), next) =>
        val bytesInNext = next.length
        val bytesFromNext = Math.max(0, Math.min(bytesTotal - bytesConsumed, bytesInNext.toLong))
        (bytesConsumed + bytesInNext, next.take(bytesFromNext.toInt))
      }
      .takeWhile(_._1 < bytesTotal, inclusive = true)
      .map(_._2)

  private def streamToEntity(contentType: ContentType, contentLength: Option[Long], stream: PekkoStreams.BinaryStream): ResponseEntity = {
    contentLength match {
      case None    => HttpEntity(contentType, stream)
      case Some(l) => HttpEntity(contentType, l, stream)
    }
  }

  private def rawPartToBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T]): Option[Multipart.FormData.BodyPart] = {
    m.partType(part.name).map { partType =>
      val partContentType = part.contentType.map(parseContentType).getOrElse(ContentTypes.`application/octet-stream`)
      val partContentLength = part.header(HeaderNames.ContentLength).flatMap(v => Try(v.toLong).toOption)
      val body = rawValueToResponseEntity(partType.asInstanceOf[RawBodyType[Any]], partContentType, partContentLength, part.body) match {
        case b: BodyPartEntity => overrideContentTypeIfDefined(b, part)
        case _                 => throw new IllegalArgumentException(s"$partType is not supported in multipart bodies")
      }

      Multipart.FormData
        .BodyPart(
          part.name,
          body,
          part.otherDispositionParams,
          parseHeadersOrThrowWithoutContentHeaders(part).toList
        )
    }
  }

  private def formatToContentType(format: CodecFormat, charset: Option[Charset]): ContentType = {
    format match {
      case CodecFormat.Json()        => ContentTypes.`application/json`
      case CodecFormat.TextPlain()   => MediaTypes.`text/plain`.withCharset(charsetToHttpCharset(charset.getOrElse(StandardCharsets.UTF_8)))
      case CodecFormat.TextHtml()    => MediaTypes.`text/html`.withCharset(charsetToHttpCharset(charset.getOrElse(StandardCharsets.UTF_8)))
      case CodecFormat.OctetStream() => MediaTypes.`application/octet-stream`
      case CodecFormat.Zip()         => MediaTypes.`application/zip`
      case CodecFormat.XWwwFormUrlencoded() => MediaTypes.`application/x-www-form-urlencoded`
      case CodecFormat.MultipartFormData()  => MediaTypes.`multipart/form-data`
      case f                                =>
        val mt = if (f.mediaType.isText) charset.fold(f.mediaType)(f.mediaType.charset(_)) else f.mediaType
        parseContentType(mt.toString())
    }
  }

  private def parseContentType(ct: String): ContentType =
    ContentTypeCache.getOrParse(ct)

  private def charsetToHttpCharset(charset: Charset): HttpCharset = HttpCharset.custom(charset.name())

  private def overrideContentTypeIfDefined[RE <: ResponseEntity](re: RE, headers: HasHeaders): RE = {
    headers.contentType match {
      case Some(ct) => re.withContentType(parseContentType(ct)).asInstanceOf[RE]
      case None     => re
    }
  }
}
