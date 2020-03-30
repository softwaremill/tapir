package sttp.tapir.server.play

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.ByteString
import play.api.http.{ContentTypes, HeaderNames, HttpEntity}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.mvc.{Codec, MultipartFormData, ResponseHeader, Result}
import sttp.model.{MediaType, Part, StatusCode}
import sttp.tapir.server.internal.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecForOptional,
  CodecFormat,
  CodecMeta,
  EndpointOutput,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawPart,
  StringValueType
}

object OutputToPlayResponse {
  def apply[O](
      defaultStatus: StatusCode,
      output: EndpointOutput[O],
      v: Any
  ): Result = {
    val outputValues = encodeOutputs(output, v, OutputValues.empty)
    val headers: Map[String, String] = outputValues.headers
      .foldLeft(Map.empty[String, List[String]]) { (a, b) =>
        if (a.contains(b._1)) a + (b._1 -> (a(b._1) :+ b._2)) else a + (b._1 -> List(b._2))
      }
      .map {
        // See comment in play.api.mvc.CookieHeaderEncoding
        case (key, value) if key == HeaderNames.SET_COOKIE => (key, value.mkString(";;"))
        case (key, value)                                  => (key, value.mkString(", "))
      }
    val status = outputValues.statusCode.getOrElse(defaultStatus)

    outputValues.body match {
      case Some(entity) =>
        val result = Result(ResponseHeader(status.code, headers), entity)
        headers.find(_._1.toLowerCase == "content-type").map(ct => result.as(ct._2)).getOrElse(result)
      case None => Result(ResponseHeader(status.code, headers), HttpEntity.NoEntity)
    }
  }

  private val encodeOutputs: EncodeOutputs[HttpEntity] =
    new EncodeOutputs[HttpEntity](new EncodeOutputBody[HttpEntity] {
      override def rawValueToBody(v: Any, codec: CodecForOptional[_, _ <: CodecFormat, Any]): HttpEntity =
        rawValueToResponseEntity(codec.meta, v)
      override def streamValueToBody(v: Any, codecFormat: CodecFormat): HttpEntity =
        HttpEntity.Streamed(v.asInstanceOf[Source[ByteString, _]], None, mediaTypeToContentFormat(codecFormat.mediaType))
    })

  private def rawValueToResponseEntity[M <: CodecFormat, R](codecMeta: CodecMeta[_, M, R], r: R): HttpEntity = {
    val contentType = mediaTypeToContentFormat(codecMeta.format.mediaType)

    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        val str = r.asInstanceOf[String]
        HttpEntity.Strict(ByteString(str, charset), contentType)

      case ByteArrayValueType =>
        val bytes = r.asInstanceOf[Array[Byte]]
        HttpEntity.Strict(ByteString(bytes), contentType)

      case ByteBufferValueType =>
        val byteBuffer = r.asInstanceOf[ByteBuffer]
        HttpEntity.Strict(ByteString(byteBuffer), contentType)

      case InputStreamValueType =>
        val stream = r.asInstanceOf[InputStream]
        HttpEntity.Streamed(StreamConverters.fromInputStream(() => stream), None, contentType)

      case FileValueType =>
        val path = r.asInstanceOf[File].toPath
        val fileSize = Some(Files.size(path))
        val file = FileIO.fromPath(path)
        HttpEntity.Streamed(file, fileSize, contentType)

      case mvt: MultipartValueType =>
        val rawParts = r.asInstanceOf[Seq[RawPart]]

        val dataParts = rawParts
          .filter { part =>
            mvt.partCodecMeta(part.name).exists { rawPart =>
              rawPart.rawValueType match {
                case StringValueType(_)  => true
                case ByteArrayValueType  => true
                case ByteBufferValueType => true
                case _                   => false
              }
            }
          }
          .flatMap(rawPartsToDataPart(mvt, _))

        val fileParts = rawParts
          .filter { part =>
            mvt.partCodecMeta(part.name).exists { rawPart =>
              rawPart.rawValueType match {
                case InputStreamValueType => true
                case FileValueType        => true
                case _                    => false
              }
            }
          }
          .flatMap(rawPartsToFilePart(mvt, _))

        HttpEntity.Streamed(multipartFormToStream(dataParts, fileParts), None, contentType)
    }
  }

  private def rawPartsToFilePart[T](
      mvt: MultipartValueType,
      part: Part[T]
  ): Option[MultipartFormData.FilePart[Source[ByteString, _]]] = {
    mvt.partCodecMeta(part.name).flatMap { codecMeta =>
      val entity: HttpEntity = rawValueToResponseEntity(codecMeta.asInstanceOf[CodecMeta[_, _ <: CodecFormat, Any]], part.body)

      for {
        fileName <- part.fileName
        contentLength <- entity.contentLength
        dispositionType <- part.otherDispositionParams.get(part.name)
      } yield MultipartFormData.FilePart(part.name, fileName, entity.contentType, entity.dataStream, contentLength, dispositionType)
    }
  }

  private def rawPartsToDataPart[T](
      mvt: MultipartValueType,
      part: Part[T]
  ): Option[MultipartFormData.DataPart] = {
    mvt.partCodecMeta(part.name).flatMap { codecMeta =>
      val charset = codecMeta.rawValueType match {
        case valueType: StringValueType => valueType.charset
        case _                          => Charset.defaultCharset()
      }

      val maybeData: Option[String] =
        rawValueToResponseEntity(codecMeta.asInstanceOf[CodecMeta[_, _ <: CodecFormat, Any]], part.body) match {
          case HttpEntity.Strict(data, _)   => Some(data.decodeString(charset))
          case HttpEntity.Streamed(_, _, _) => None
          case HttpEntity.Chunked(_, _)     => None
        }

      maybeData.map(MultipartFormData.DataPart(part.name, _))
    }
  }

  private def mediaTypeToContentFormat(mediaType: MediaType): Option[String] = {
    val result = mediaType.copy(charset = mediaType.charset.map(_.toLowerCase)) match {
      case MediaType.ApplicationJson               => ContentTypes.JSON
      case MediaType.TextPlain                     => ContentTypes.TEXT(Codec.javaSupported(mediaType.charset.getOrElse("utf-8")))
      case MediaType.TextPlainUtf8                 => ContentTypes.TEXT(Codec.utf_8)
      case MediaType.ApplicationOctetStream        => ContentTypes.BINARY
      case MediaType.ApplicationXWwwFormUrlencoded => ContentTypes.FORM
      case MediaType.MultipartFormData             => "multipart/form-data"
      case _                                       => throw new IllegalArgumentException(s"Cannot parse content type: $mediaType")
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
        .flatMap {
          case DataPart(name, value) =>
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
