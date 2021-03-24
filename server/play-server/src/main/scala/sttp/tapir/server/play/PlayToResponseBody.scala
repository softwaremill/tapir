package sttp.tapir.server.play

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.ByteString
import play.api.http.{ContentTypes, HeaderNames, HttpEntity}
import play.api.mvc.{Codec, MultipartFormData}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import sttp.model.{HasHeaders, MediaType, Part}
import sttp.tapir.internal.NoStreams
import sttp.tapir.{CodecFormat, RawBodyType, RawPart, WebSocketBodyOutput}
import sttp.tapir.server.interpreter.ToResponseBody

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files

class PlayToResponseBody extends ToResponseBody[HttpEntity, Nothing] {
  override val streams: NoStreams = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): HttpEntity = {
    fromRawValue(v, headers, bodyType)
  }

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
        val stream = v.asInstanceOf[InputStream]
        HttpEntity.Streamed(StreamConverters.fromInputStream(() => stream), None, contentType)

      case RawBodyType.FileBody =>
        val path = v.asInstanceOf[File].toPath
        val fileSize = Some(Files.size(path))
        val file = FileIO.fromPath(path)
        HttpEntity.Streamed(file, fileSize, contentType)

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

  override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): HttpEntity =
    throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
  ): HttpEntity = throw new UnsupportedOperationException

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

  private def formatToContentType(format: CodecFormat): Option[String] = {
    val result = format.mediaType.copy(charset = format.mediaType.charset.map(_.toLowerCase)) match {
      case MediaType.ApplicationJson               => ContentTypes.JSON
      case MediaType.TextPlain                     => ContentTypes.TEXT(Codec.javaSupported(format.mediaType.charset.getOrElse("utf-8")))
      case MediaType.TextPlainUtf8                 => ContentTypes.TEXT(Codec.utf_8)
      case MediaType.TextHtml                      => ContentTypes.HTML(Codec.utf_8)
      case MediaType.ApplicationOctetStream        => ContentTypes.BINARY
      case MediaType.ApplicationXWwwFormUrlencoded => ContentTypes.FORM
      case MediaType.MultipartFormData             => "multipart/form-data"
      case m                                       => m.toString()
    }

    Option(result)
  }

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
