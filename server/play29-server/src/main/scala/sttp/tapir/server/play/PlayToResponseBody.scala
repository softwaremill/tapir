package sttp.tapir.server.play

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.ByteString
import play.api.http.{HeaderNames, HttpChunk, HttpEntity}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.mvc.{Codec, MultipartFormData}
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{HasHeaders, Part}
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, RawPart, WebSocketBodyOutput}

import java.nio.ByteBuffer
import java.nio.charset.Charset

class PlayToResponseBody extends ToResponseBody[PlayResponseBody, AkkaStreams] {

  override val streams: AkkaStreams = AkkaStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): PlayResponseBody = {
    Right(fromRawValue(v, headers, bodyType))
  }

  private val ChunkSize = 8192

  private def fromRawValue[R](v: R, headers: HasHeaders, bodyType: RawBodyType[R]): HttpEntity = {
    val contentType = headers.contentType
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val str = v.asInstanceOf[String]
        HttpEntity.Strict(ByteString(str, charset), contentType)

      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        HttpEntity.Strict(ByteString(bytes), contentType)

      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        HttpEntity.Strict(ByteString(byteBuffer), contentType)

      case RawBodyType.InputStreamBody =>
        streamOrChunk(StreamConverters.fromInputStream(() => v), headers.contentLength, contentType)

      case RawBodyType.InputStreamRangeBody =>
        val initialStream = StreamConverters.fromInputStream(v.inputStreamFromRangeStart, ChunkSize)
        v.range
          .map(r => streamOrChunk(toRangedStream(initialStream, bytesTotal = r.contentLength), Some(r.contentLength), contentType))
          .getOrElse(streamOrChunk(initialStream, headers.contentLength, contentType))

      case RawBodyType.FileBody =>
        v.range
          .flatMap(r =>
            r.startAndEnd
              .map(s => streamOrChunk(createFileSource(v, s._1, r.contentLength), Some(r.contentLength), contentType))
          )
          .getOrElse(streamOrChunk(FileIO.fromPath(v.file.toPath), Some(v.file.length()), contentType))

      case m: RawBodyType.MultipartBody =>
        val rawParts = v.asInstanceOf[Seq[RawPart]]

        val dataParts = rawParts
          .filter { part =>
            m.partType(part.name).exists {
              case RawBodyType.StringBody(_)  => true
              case RawBodyType.ByteArrayBody  => true
              case RawBodyType.ByteBufferBody => true
              case _                          => false
            }
          }
          .flatMap(rawPartsToDataPart(m, _))

        val fileParts = rawParts
          .filter { part =>
            m.partType(part.name).exists {
              case RawBodyType.InputStreamBody => true
              case RawBodyType.FileBody        => true
              case _                           => false
            }
          }
          .flatMap(rawPartsToFilePart(m, _))

        HttpEntity.Streamed(multipartFormToStream(dataParts, fileParts), None, contentType)
    }
  }

  private def createFileSource(
      tapirFile: FileRange,
      start: Long,
      bytesTotal: Long
  ): AkkaStreams.BinaryStream =
    toRangedStream(FileIO.fromPath(tapirFile.file.toPath, ChunkSize, startPosition = start), bytesTotal)

  private def toRangedStream(initialStream: AkkaStreams.BinaryStream, bytesTotal: Long): AkkaStreams.BinaryStream =
    initialStream
      .scan((0L, ByteString.empty)) { case ((bytesConsumed, _), next) =>
        val bytesInNext = next.length
        val bytesFromNext = Math.max(0, Math.min(bytesTotal - bytesConsumed, bytesInNext.toLong))
        (bytesConsumed + bytesInNext, next.take(bytesFromNext.toInt))
      }
      .takeWhile(_._1 < bytesTotal, inclusive = true)
      .map(_._2)

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): PlayResponseBody = {
    Right(streamOrChunk(v, headers.contentLength, Option(headers.contentType.getOrElse(formatToContentType(format, charset)))))
  }

  private def streamOrChunk(stream: streams.BinaryStream, contentLength: Option[Long], contentType: Option[String]): HttpEntity = {
    contentLength match {
      case Some(length) =>
        HttpEntity.Streamed(stream, Some(length), contentType)
      case None =>
        val chunkStream = stream.map(HttpChunk.Chunk.apply)
        HttpEntity.Chunked(chunkStream, contentType)
    }
  }

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, AkkaStreams]
  ): PlayResponseBody = Left(PlayWebSockets.pipeToBody(pipe, o))

  private def rawPartsToFilePart[T](
      m: RawBodyType.MultipartBody,
      part: Part[T]
  ): Option[MultipartFormData.FilePart[Source[ByteString, _]]] = {
    m.partType(part.name).flatMap { partType =>
      val entity: HttpEntity = fromRawValue(part.body, part, partType.asInstanceOf[RawBodyType[Any]])

      for {
        fileName <- part.fileName
        contentLength <- entity.contentLength
        dispositionType <- part.otherDispositionParams.get(part.name)
      } yield MultipartFormData.FilePart(part.name, fileName, entity.contentType, entity.dataStream, contentLength, dispositionType)
    }
  }

  private def rawPartsToDataPart[T](m: RawBodyType.MultipartBody, part: Part[T]): Option[MultipartFormData.DataPart] = {
    m.partType(part.name).flatMap { partType =>
      val charset = partType match {
        case valueType: RawBodyType.StringBody => valueType.charset
        case _                                 => Charset.defaultCharset()
      }

      val maybeData: Option[String] =
        fromRawValue(part.body, part, partType.asInstanceOf[RawBodyType[Any]]) match {
          case HttpEntity.Strict(data, _)   => Some(data.decodeString(charset))
          case HttpEntity.Streamed(_, _, _) => None
          case HttpEntity.Chunked(_, _)     => None
        }

      maybeData.map(MultipartFormData.DataPart(part.name, _))
    }
  }

  private def formatToContentType(format: CodecFormat, charset: Option[Charset]): String =
    charset.fold(format.mediaType)(format.mediaType.charset(_)).toString()

  private def multipartFormToStream[A](
      dataParts: Seq[DataPart],
      fileParts: Seq[FilePart[Source[ByteString, _]]]
  ): Source[ByteString, NotUsed] = {
    val boundary: String = "--------" + scala.util.Random.alphanumeric.take(20).mkString("")

    def formatDataParts(dataParts: Seq[DataPart]) = {
      val result = dataParts
        .flatMap { case DataPart(name, value) =>
          s"""
              --$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: form-data; name="$name"\r\n\r\n$value\r\n
            """.stripMargin
        }
        .mkString("")
      Codec.utf_8.encode(result)
    }

    def filePartHeader(file: FilePart[_]) = {
      val name = s""""${file.key}""""
      val filename = s""""${file.filename}""""
      val contentType = file.contentType
        .map { ct => s"${HeaderNames.CONTENT_TYPE}: $ct\r\n" }
        .getOrElse("")
      Codec.utf_8.encode(
        s"--$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: form-data; name=$name; filename=$filename\r\n$contentType\r\n"
      )
    }

    Source
      .single(formatDataParts(dataParts))
      .concat(Source(fileParts.toList).flatMapConcat { file =>
        Source
          .single(filePartHeader(file))
          .concat(file.ref)
          .concat(Source.single(ByteString("\r\n", Charset.forName("UTF-8"))))
          .concat(Source.single(ByteString(s"--$boundary--", "UTF-8")))
      })
  }
}
