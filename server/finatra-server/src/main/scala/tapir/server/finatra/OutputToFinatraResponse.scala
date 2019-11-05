package tapir.server.finatra

import java.io.{File, InputStream}
import java.nio.ByteBuffer

import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.{Buf, InputStreamReader, Reader}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content._
import org.apache.http.entity.mime.{FormBodyPart, FormBodyPartBuilder, MultipartEntityBuilder}
import sttp.model.{Header, Part}
import tapir.internal.server.{EncodeOutputBody, EncodeOutputs, OutputValues}
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecForOptional,
  CodecMeta,
  EndpointOutput,
  FileValueType,
  InputStreamValueType,
  CodecFormat,
  MultipartValueType,
  RawPart,
  StringValueType
}

object OutputToFinatraResponse {
  private val encodeOutputs: EncodeOutputs[(FinatraContent, String)] = new EncodeOutputs(new EncodeOutputBody[(FinatraContent, String)] {
    override def rawValueToBody(v: Any, codec: CodecForOptional[_, _ <: CodecFormat, Any]): (FinatraContent, String) =
      rawValueToFinatraContent(codec.meta, v)
    override def streamValueToBody(v: Any, format: CodecFormat): (FinatraContent, String) = {
      FinatraContentBuf(v.asInstanceOf[Buf]) -> format.mediaType.toString()
    }
  })

  def apply[O](
      defaultStatus: Status,
      output: EndpointOutput[O],
      v: Any
  ): Response = {
    outputValuesToResponse(encodeOutputs(output, v, OutputValues.empty), defaultStatus)
  }

  private def outputValuesToResponse(outputValues: OutputValues[(FinatraContent, String)], defaultStatus: Status): Response = {
    val status = outputValues.statusCode.map(sc => Status(sc.code)).getOrElse(defaultStatus)

    val responseWithContent = outputValues.body match {
      case Some((FinatraContentBuf(buf), ct)) =>
        val response = Response(Version.Http11, status)
        response.content = buf
        response.contentType = ct
        response
      case Some((FinatraContentReader(reader), ct)) =>
        val response = Response(Version.Http11, status, reader)
        response.contentType = ct
        response
      case None =>
        Response(Version.Http11, status)
    }

    outputValues.headers.foreach { case (name, value) => responseWithContent.headerMap.add(name, value) }

    // If there's a content-type header in headers, override the content-type.
    outputValues.headers.find(_._1.toLowerCase == "content-type").foreach {
      case (_, value) => responseWithContent.contentType = value
    }

    responseWithContent
  }

  private def rawValueToFinatraContent[CF <: CodecFormat, R](codecMeta: CodecMeta[_, CF, R], r: R): (FinatraContent, String) = {
    val ct: String = codecMeta.format.mediaType.toString()

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

        // inputStream is split out into a val because otherwise it doesn't compile in 2.11
        val inputStream: InputStream = entity.build().getContent

        FinatraContentReader(InputStreamReader(inputStream)) -> ct
    }
  }

  private def rawValueToContentBody[CF <: CodecFormat, R](codecMeta: CodecMeta[_, CF, R], part: Part[R], r: R): ContentBody = {
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
      case _: MultipartValueType =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
    }
  }

  private def rawPartToFormBodyPart[R](mvt: MultipartValueType, part: Part[R]): Option[FormBodyPart] = {
    mvt.partCodecMeta(part.name).map { codecMeta =>
      val builder = FormBodyPartBuilder
        .create(
          part.name,
          rawValueToContentBody(codecMeta.asInstanceOf[CodecMeta[_, _ <: CodecFormat, Any]], part.asInstanceOf[Part[Any]], part.body)
        )

      part.headers.foreach { case Header(name, value) => builder.addField(name, value) }

      builder.build()
    }
  }
}
