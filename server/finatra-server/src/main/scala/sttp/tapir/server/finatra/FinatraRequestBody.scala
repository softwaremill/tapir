package sttp.tapir.server.finatra

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.request.RequestUtils
import com.twitter.io.Buf
import com.twitter.util.Future
import org.apache.commons.fileupload.FileItemHeaders
import sttp.capabilities.Streams
import sttp.model.{Header, Part}
import sttp.tapir.{RawBodyType, RawPart}
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.interpreter.RequestBody

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.collection.immutable.Seq
import scala.collection.JavaConverters._

class FinatraRequestBody(request: Request, serverOptions: FinatraServerOptions) extends RequestBody[Future, Nothing] {
  override val streams: Streams[Nothing] = NoStreams

  override def toRaw[R](bodyType: RawBodyType[R]): Future[R] = toRaw(bodyType, request.content, request.charset.map(Charset.forName))

  private def toRaw[R](bodyType: RawBodyType[R], body: Buf, charset: Option[Charset]): Future[R] = {
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
      case RawBodyType.StringBody(defaultCharset) => Future.value[R](new String(asByteArray, charset.getOrElse(defaultCharset)))
      case RawBodyType.ByteArrayBody              => Future.value[R](asByteArray)
      case RawBodyType.ByteBufferBody             => Future.value[R](asByteBuffer)
      case RawBodyType.InputStreamBody            => Future.value[R](new ByteArrayInputStream(asByteArray))
      case RawBodyType.FileBody                   => serverOptions.createFile(asByteArray)
      case m: RawBodyType.MultipartBody           => multiPartRequestToRawBody(request, m)
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
              futureBody = toRaw(partType, Buf.ByteArray.Owned(multiPartItem.data), charset)
            } yield futureBody
              .map(body =>
                Part(name, body, otherDispositionParams = dispositionParams - "name", headers = fileItemHeaders(multiPartItem.headers))
                  .asInstanceOf[RawPart]
              )
          }
          .toSeq
      )
      .map(_.toList)
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()
}
