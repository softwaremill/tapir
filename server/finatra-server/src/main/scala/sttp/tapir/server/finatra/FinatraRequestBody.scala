package sttp.tapir.server.finatra

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.request.RequestUtils
import com.twitter.io.Buf
import com.twitter.util.Future
import org.apache.commons.fileupload.FileItemHeaders
import sttp.model.{Header, Part}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, RawPart}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

class FinatraRequestBody(serverOptions: FinatraServerOptions) extends RequestBody[Future, NoStreams] {
  override val streams: NoStreams = NoStreams

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Future[RawValue[R]] = {
    val request = finatraRequest(serverRequest)
    toRaw(request, bodyType, request.content, request.charset.map(Charset.forName))
  }

  private def toRaw[R](request: Request, bodyType: RawBodyType[R], body: Buf, charset: Option[Charset]): Future[RawValue[R]] = {
    def asByteArray: Array[Byte] = {
      val array = new Array[Byte](body.length)
      body.write(array, 0)
      array
    }

    def asByteBuffer: ByteBuffer = {
      val buffer = ByteBuffer.allocate(body.length)
      body.write(buffer)
      buffer.flip()
      buffer
    }

    bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        Future.value[R](new String(asByteArray, charset.getOrElse(defaultCharset))).map(RawValue(_))
      case RawBodyType.ByteArrayBody        => Future.value[R](asByteArray).map(RawValue(_))
      case RawBodyType.ByteBufferBody       => Future.value[R](asByteBuffer).map(RawValue(_))
      case RawBodyType.InputStreamBody      => Future.value[R](new ByteArrayInputStream(asByteArray)).map(RawValue(_))
      case RawBodyType.InputStreamRangeBody =>
        Future.value[R](InputStreamRange(() => new ByteArrayInputStream(asByteArray))).map(RawValue(_))

      case RawBodyType.FileBody => serverOptions.createFile(asByteArray).map(f => FileRange(f)).map(file => RawValue(file, Seq(file)))
      case m: RawBodyType.MultipartBody => multiPartRequestToRawBody(request, m).map(RawValue.fromParts)
    }
  }

  private def parseDispositionParams(headerValue: Option[String]): Map[String, String] =
    headerValue
      .map(
        _.split(";")
          .map(_.trim)
          .tail
          .map(_.split("="))
          .map(array => array(0) -> array(1))
          .toMap
      )
      .getOrElse(Map.empty)

  private def getCharset(contentType: Option[String]): Option[Charset] =
    contentType.flatMap(
      _.split(";")
        .map(_.trim)
        .tail
        .map(_.split("="))
        .map(array => array(0) -> array(1))
        .toMap
        .get("charset")
        .map(Charset.forName)
    )

  private def multiPartRequestToRawBody(request: Request, m: RawBodyType.MultipartBody): Future[Seq[RawPart]] = {
    def fileItemHeaders(headers: FileItemHeaders): Seq[Header] = {
      headers.getHeaderNames.asScala
        .flatMap { name => headers.getHeaders(name).asScala.map(name -> _) }
        .toSeq
        .filter(_._1.toLowerCase != "content-disposition")
        .map { case (k, v) => Header(k, v) }
        .toList
    }

    Future
      .collect(
        RequestUtils
          .multiParams(request)
          .flatMap { case (name, multiPartItem) =>
            val dispositionParams: Map[String, String] =
              parseDispositionParams(Option(multiPartItem.headers.getHeader("content-disposition")))
            val charset = getCharset(multiPartItem.contentType)

            for {
              partType <- m.partType(name)
              futureBody = toRaw(request, partType, Buf.ByteArray.Owned(multiPartItem.data), charset)
            } yield futureBody
              .map(body =>
                Part(
                  name,
                  body.value,
                  otherDispositionParams = dispositionParams - "name",
                  headers = fileItemHeaders(multiPartItem.headers)
                )
              )
          }
          .toSeq
      )
      .map(_.toList)
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()

  private def finatraRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]
}
