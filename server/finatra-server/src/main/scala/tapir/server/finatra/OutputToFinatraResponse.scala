package tapir.server.finatra
import java.io.{File, InputStream}
import java.nio.ByteBuffer

import com.twitter.finagle.http.Status
import com.twitter.io.{Buf, InputStreamReader, Reader}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content._
import org.apache.http.entity.mime.{FormBodyPart, FormBodyPartBuilder, MultipartEntityBuilder}
import tapir.internal.{ParamsToSeq, _}
import tapir.model.Part
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecMeta,
  EndpointIO,
  EndpointOutput,
  FileValueType,
  InputStreamValueType,
  MediaType,
  MultipartValueType,
  RawPart,
  StreamingEndpointIO,
  StringValueType
}

object OutputToFinatraResponse {
  def apply[O](
      output: EndpointOutput[O],
      v: Any,
      startingResponse: Option[FinatraResponse] = None,
      defaultStatus: Status = Status.Ok
  ): FinatraResponse = {
    val vs = ParamsToSeq(v)

    output.asVectorOfSingleOutputs.zipWithIndex.foldLeft(startingResponse.getOrElse(FinatraResponse(defaultStatus))) {
      case (finatraResponse, input) =>
        input match {
          case (EndpointIO.Body(codec, _), i) =>
            codec.encode(vs(i)).map(rawValueToFinatraContent(codec.meta, _)) match {
              case Some((content, contentType)) =>
                finatraResponse.copy(content = content, contentType = contentType)
              case None =>
                finatraResponse
            }

          case (EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, mediaType, _)), i) =>
            finatraResponse.copy(contentType = mediaType.mediaType, content = FinatraContentBuf(vs(i).asInstanceOf[Buf]))

          case (EndpointIO.Header(name, codec, _), i) =>
            codec
              .encode(vs(i))
              .foldLeft(finatraResponse) {
                case (fr, value) => fr.setOrAddHeader(name, value)
              }

          case (EndpointIO.Headers(_), i) =>
            vs(i).asInstanceOf[Seq[(String, String)]].foldLeft(finatraResponse) {
              case (fr, (name, value)) => fr.setOrAddHeader(name, value)
            }

          case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
            apply(wrapped, g(vs(i)), Some(finatraResponse))

          case (EndpointOutput.StatusCode(), i) =>
            finatraResponse.copy(status = Status(vs(i).asInstanceOf[Int]))

          case (EndpointOutput.StatusFrom(io, default, _, when), i) =>
            val v = vs(i)
            val sc = when.find(_._1.matches(v)).map(_._2).getOrElse(default)
            apply(io, v).copy(status = Status(sc))

          case (EndpointOutput.Mapped(wrapped, _, g, _), i) =>
            apply(wrapped, g(vs(i)), Some(finatraResponse))
        }
    }

  }

  private def rawValueToFinatraContent[M <: MediaType, R](codecMeta: CodecMeta[M, R], r: R): (FinatraContent, String) = {
    val ct: String = codecMeta.mediaType.mediaType

    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        FinatraContentBuf(Buf.ByteArray.Owned(r.toString.getBytes(charset))) -> ct
      case ByteArrayValueType  => FinatraContentBuf(Buf.ByteArray.Owned(r)) -> ct
      case ByteBufferValueType => FinatraContentBuf(Buf.ByteBuffer.Owned(r)) -> ct
      case InputStreamValueType =>
        FinatraContentReader(Reader.fromStream(r: InputStream)) -> ct
      case FileValueType =>
        FinatraContentReader(Reader.fromFile(r: File)) -> ct
      case mvt: MultipartValueType =>
        val entity = MultipartEntityBuilder.create()

        (r: Seq[RawPart]).flatMap(rawPartToFormBodyPart(mvt, _)).foreach { formBodyPart: FormBodyPart =>
          entity.addPart(formBodyPart)
        }

        FinatraContentReader(InputStreamReader(entity.build().getContent)) -> ct
    }
  }

  private def rawValueToContentBody[M <: MediaType, R](codecMeta: CodecMeta[M, R], part: Part[R], r: R): ContentBody = {
    val contentType: String = part.header("content-type").getOrElse("text/plain")

    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        new StringBody(r.toString, ContentType.create(contentType, charset))
      case ByteArrayValueType =>
        new ByteArrayBody(r: Array[Byte], ContentType.create(contentType), part.fileName.get)
      case ByteBufferValueType =>
        val array: Array[Byte] = new Array[Byte]((r: ByteBuffer).remaining)
        (r: ByteBuffer).get(array)
        new ByteArrayBody(array, ContentType.create(contentType), part.fileName.get)
      case FileValueType =>
        part.fileName match {
          case Some(filename) => new FileBody(r: File, ContentType.create(contentType), filename)
          case None           => new FileBody(r: File, ContentType.create(contentType))
        }
      case InputStreamValueType =>
        new InputStreamBody(r: InputStream, ContentType.create(contentType), part.fileName.get)
      case _ =>
        ???
    }
  }

  private def rawPartToFormBodyPart[R](mvt: MultipartValueType, part: Part[R]): Option[FormBodyPart] = {
    mvt.partCodecMeta(part.name).map { codecMeta =>
      val builder = FormBodyPartBuilder
        .create(
          part.name,
          rawValueToContentBody(codecMeta.asInstanceOf[CodecMeta[_ <: MediaType, Any]], part.asInstanceOf[Part[Any]], part.body)
        )

      part.headers.foreach { case (name, value) => builder.addField(name, value) }

      builder.build()
    }
  }

}
