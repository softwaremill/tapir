package tapir.server.play

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

import akka.stream.Materializer
import play.api.http.HttpEntity
import play.api.mvc.{AnyContent, RawBuffer, Request}
import tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawValueType,
  StringValueType
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PlayRequestToRawBody {
  def apply[R](rawBodyType: RawValueType[R], body: RawBuffer, charset: Option[Charset], request: Request[R])(
      implicit mat: Materializer
  ): Future[R] = {

    rawBodyType match {
      case StringValueType(defaultCharset) => Future(new String(body.asBytes().get.toArray, charset.getOrElse(defaultCharset)))
      case ByteArrayValueType              => Future(body.asBytes().get.toArray)
      case ByteBufferValueType             => Future(body.asBytes().get.toByteBuffer)
      case InputStreamValueType            => Future(body.asBytes().get.toArray).map(new ByteArrayInputStream(_))
      case FileValueType                   => ??? //serverOptions.createFile(asByteArray)
      case mvt: MultipartValueType         => ??? ///multiPartRequestToRawBody(request, mvt)
    }
  }

}
