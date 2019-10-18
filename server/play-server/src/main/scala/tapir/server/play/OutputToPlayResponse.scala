package tapir.server.play

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.file.Files

import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.ByteString
import play.api.http.HttpEntity
import play.api.mvc.{ResponseHeader, Result}
import tapir.internal.server.{EncodeOutputBody, EncodeOutputs, OutputValues}
import tapir.model.StatusCode
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecForOptional,
  CodecMeta,
  EndpointOutput,
  FileValueType,
  InputStreamValueType,
  MediaType,
  MultipartValueType,
  StringValueType
}

object OutputToPlayResponse {

  def apply[O](
      defaultStatus: StatusCode,
      output: EndpointOutput[O],
      v: Any
  ): Result = {
    val outputValues = encodeOutputs(output, v, OutputValues.empty)
    val headers: Map[String, String] = outputValues.headers.toMap
    val status = outputValues.statusCode.getOrElse(defaultStatus)

    outputValues.body match {
      case Some(entity) => Result(ResponseHeader(status, headers), entity)
      case None         => Result(ResponseHeader(status, headers), HttpEntity.NoEntity)
    }
  }

  private val encodeOutputs: EncodeOutputs[HttpEntity] =
    new EncodeOutputs[HttpEntity](new EncodeOutputBody[HttpEntity] {
      override def rawValueToBody(v: Any, codec: CodecForOptional[_, _ <: MediaType, Any]): HttpEntity =
        rawValueToResponseEntity(codec.meta, v)
      override def streamValueToBody(v: Any, mediaType: MediaType): HttpEntity =
        HttpEntity.Streamed(v.asInstanceOf[Source[ByteString, _]], None, mediaTypeToContentType(mediaType))
    })

  private def rawValueToResponseEntity[M <: MediaType, R](codecMeta: CodecMeta[_, M, R], r: R): HttpEntity = {
    val contentType = mediaTypeToContentType(codecMeta.mediaType)

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

      case MultipartValueType(partCodecMetas, defaultCodecMeta) => ???
    }
  }

  private def mediaTypeToContentType(mediaType: MediaType): Option[String] = {
    val result = mediaType match {
      case MediaType.Json()               => "application/json"
      case MediaType.TextPlain(charset)   => "text/plain"
      case MediaType.OctetStream()        => "application/octet-stream"
      case MediaType.XWwwFormUrlencoded() => "application/x-www-form-urlencoded"
      case MediaType.MultipartFormData()  => "multipart/form-data"
      case _                              => throw new IllegalArgumentException(s"Cannot parse content type: $mediaType")
    }

    Option(result)
  }

}
