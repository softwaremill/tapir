package sttp.tapir.server.finatra

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.request.RequestUtils
import com.twitter.io.Buf
import com.twitter.util.Future
import org.apache.commons.fileupload.FileItemHeaders
import sttp.model.{Header, Part}
import sttp.tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawPart,
  RawValueType,
  StringValueType
}

import scala.collection.JavaConverters._

class FinatraRequestToRawBody(serverOptions: FinatraServerOptions) {
  def apply[R](rawBodyType: RawValueType[R], body: Buf, charset: Option[Charset], request: Request): Future[R] = {
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

    rawBodyType match {
      case StringValueType(defaultCharset) => Future.value[R](new String(asByteArray, charset.getOrElse(defaultCharset)))
      case ByteArrayValueType              => Future.value[R](asByteArray)
      case ByteBufferValueType             => Future.value[R](asByteBuffer)
      case InputStreamValueType            => Future.value[R](new ByteArrayInputStream(asByteArray))
      case FileValueType                   => serverOptions.createFile(asByteArray)
      case mvt: MultipartValueType         => multiPartRequestToRawBody(request, mvt)
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

  private def getCharset(contentType: Option[String]): Option[Charset] = contentType.flatMap(
    _.split(";")
      .map(_.trim)
      .tail
      .map(_.split("="))
      .map(array => array(0) -> array(1))
      .toMap
      .get("charset")
      .map(Charset.forName)
  )

  private def multiPartRequestToRawBody(request: Request, mvt: MultipartValueType): Future[Seq[RawPart]] = {
    def fileItemHeaders(headers: FileItemHeaders): Seq[Header] = {
      headers.getHeaderNames.asScala
        .flatMap { name =>
          headers.getHeaders(name).asScala.map(name -> _)
        }
        .toSeq
        .filter(_._1.toLowerCase != "content-disposition")
        .map { case (k, v) => Header.notValidated(k, v) }
    }

    Future.collect(
      RequestUtils
        .multiParams(request)
        .flatMap {
          case (name, multiPartItem) =>
            val dispositionParams: Map[String, String] =
              parseDispositionParams(Option(multiPartItem.headers.getHeader("content-disposition")))
            val charset = getCharset(multiPartItem.contentType)

            for {
              codecMeta <- mvt.partCodecMeta(name)
              futureBody = apply(codecMeta.rawValueType, Buf.ByteArray.Owned(multiPartItem.data), charset, request)
            } yield futureBody
              .map(body =>
                Part(name, body, otherDispositionParams = dispositionParams - "name", headers = fileItemHeaders(multiPartItem.headers))
                  .asInstanceOf[RawPart]
              )
        }
        .toSeq
    )
  }
}
