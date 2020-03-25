package sttp.tapir.server.finatra

import java.io.InputStream
import java.nio.charset.Charset

import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.{Buf, InputStreamReader, Reader}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content._
import org.apache.http.entity.mime.{FormBodyPart, FormBodyPartBuilder, MultipartEntityBuilder}
import sttp.model.{Header, Part}
import sttp.tapir.server.internal.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType}
import sttp.tapir.internal._

object OutputToFinatraResponse {
  private val encodeOutputs: EncodeOutputs[(FinatraContent, String)] = new EncodeOutputs(new EncodeOutputBody[(FinatraContent, String)] {
    override def rawValueToBody(v: Any, format: CodecFormat, bodyType: RawBodyType[_]): (FinatraContent, String) =
      rawValueToFinatraContent(bodyType.asInstanceOf[RawBodyType[Any]], formatToContentType(format, charset(bodyType)), v)
    override def streamValueToBody(v: Any, format: CodecFormat, charset: Option[Charset]): (FinatraContent, String) = {
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

  private def rawValueToFinatraContent[CF <: CodecFormat, R](bodyType: RawBodyType[R], ct: String, r: R): (FinatraContent, String) = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        FinatraContentBuf(Buf.ByteArray.Owned(r.toString.getBytes(charset))) -> ct
      case RawBodyType.ByteArrayBody  => FinatraContentBuf(Buf.ByteArray.Owned(r)) -> ct
      case RawBodyType.ByteBufferBody => FinatraContentBuf(Buf.ByteBuffer.Owned(r)) -> ct
      case RawBodyType.InputStreamBody =>
        FinatraContentReader(Reader.fromStream(r)) -> ct
      case RawBodyType.FileBody =>
        FinatraContentReader(Reader.fromFile(r)) -> ct
      case m: RawBodyType.MultipartBody =>
        val entity = MultipartEntityBuilder.create()

        r.flatMap(rawPartToFormBodyPart(m, _)).foreach { formBodyPart: FormBodyPart => entity.addPart(formBodyPart) }

        // inputStream is split out into a val because otherwise it doesn't compile in 2.11
        val inputStream: InputStream = entity.build().getContent

        FinatraContentReader(InputStreamReader(inputStream)) -> ct
    }
  }

  private def rawValueToContentBody[CF <: CodecFormat, R](bodyType: RawBodyType[R], part: Part[R], r: R): ContentBody = {
    val contentType: String = part.header("content-type").getOrElse("text/plain")

    bodyType match {
      case RawBodyType.StringBody(charset) =>
        new StringBody(r.toString, ContentType.create(contentType, charset))
      case RawBodyType.ByteArrayBody =>
        new ByteArrayBody(r, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.ByteBufferBody =>
        val array: Array[Byte] = new Array[Byte](r.remaining)
        r.get(array)
        new ByteArrayBody(array, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.FileBody =>
        part.fileName match {
          case Some(filename) => new FileBody(r, ContentType.create(contentType), filename)
          case None           => new FileBody(r, ContentType.create(contentType))
        }
      case RawBodyType.InputStreamBody =>
        new InputStreamBody(r, ContentType.create(contentType), part.fileName.get)
      case _: RawBodyType.MultipartBody =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
    }
  }

  private def rawPartToFormBodyPart[R](m: RawBodyType.MultipartBody, part: Part[R]): Option[FormBodyPart] = {
    m.partType(part.name).map { partType =>
      val builder = FormBodyPartBuilder
        .create(
          part.name,
          rawValueToContentBody(partType.asInstanceOf[RawBodyType[Any]], part.asInstanceOf[Part[Any]], part.body)
        )

      part.headers.foreach { case Header(name, value) => builder.addField(name, value) }

      builder.build()
    }
  }

  private def formatToContentType(format: CodecFormat, charset: Option[Charset]): String = {
    format match {
      case CodecFormat.Json()               => format.mediaType.toString()
      case CodecFormat.OctetStream()        => format.mediaType.toString()
      case CodecFormat.XWwwFormUrlencoded() => format.mediaType.toString()
      case CodecFormat.MultipartFormData()  => format.mediaType.toString()
      // text/plain and others
      case _ =>
        val mt = format.mediaType
        charset.map(c => mt.charset(c.toString)).getOrElse(mt).toString
    }
  }
}
