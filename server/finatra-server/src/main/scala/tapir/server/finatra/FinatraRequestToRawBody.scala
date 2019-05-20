package tapir.server.finatra
import java.io.{ByteArrayInputStream, FileOutputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.request.RequestUtils
import com.twitter.io.Buf
import org.apache.commons.fileupload.FileItemHeaders
import tapir.model.Part
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  Defaults,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawPart,
  RawValueType,
  StringValueType
}

import scala.collection.JavaConverters._

object FinatraRequestToRawBody {
  def apply[R](rawBodyType: RawValueType[R], body: Buf, charset: Option[Charset], request: Request): R = {

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

    def fileItemHeadersToSeq(headers: FileItemHeaders): Seq[(String, String)] = {
      headers.getHeaderNames.asScala
        .flatMap { name =>
          headers.getHeaders(name).asScala.map(name -> _)
        }
        .toSeq
        .filter(_._1.toLowerCase != "content-disposition")
    }

    rawBodyType match {
      case StringValueType(defaultCharset) => new String(asByteArray, charset.getOrElse(defaultCharset))
      case ByteArrayValueType              => asByteArray
      case ByteBufferValueType             => asByteBuffer
      case InputStreamValueType            => new ByteArrayInputStream(asByteArray)
      case FileValueType                   =>
        // TODO: Make this async
        val file = Defaults.createTempFile()
        val outputStream = new FileOutputStream(file)
        outputStream.write(asByteArray)
        outputStream.close()
        file
      case mvt: MultipartValueType =>
        RequestUtils
          .multiParams(request)
          .flatMap {
            case (name, multiPartItem) =>
              val dispositionParams = Option(multiPartItem.headers.getHeader("content-disposition"))
                .map(
                  _.split(";")
                    .map(_.trim)
                    .tail
                    .map(_.split("="))
                    .map(array => array(0) -> array(1))
                    .toMap
                )
                .getOrElse(Map.empty)

              val charset = multiPartItem.contentType.flatMap(
                _.split(";")
                  .map(_.trim)
                  .tail
                  .map(_.split("="))
                  .map(array => array(0) -> array(1))
                  .toMap
                  .get("charset")
                  .map(Charset.forName)
              )

              mvt
                .partCodecMeta(name)
                .map(codecMeta => apply(codecMeta.rawValueType, Buf.ByteArray.Owned(multiPartItem.data), charset, request))
                .map(
                  body => Part(name, dispositionParams - "name", fileItemHeadersToSeq(multiPartItem.headers), body).asInstanceOf[RawPart]
                )
          }
          .toSeq
    }
  }
}
