package tapir.server.play

import java.nio.charset.Charset

import play.api.http.HttpEntity
import play.api.mvc.Request
import tapir.{ByteArrayValueType, ByteBufferValueType, FileValueType, InputStreamValueType, MultipartValueType, RawValueType, StringValueType}

import scala.concurrent.Future

object PlayRequestToRawBody {
  def apply[R](rawBodyType: RawValueType[R], body: HttpEntity, charset: Charset, request: Request[R]): Future[R] = {

//    rawBodyType match {
//      case StringValueType(charset) => Future[R]()
//      case ByteArrayValueType =>
//      case ByteBufferValueType =>
//      case InputStreamValueType =>
//      case FileValueType =>
//      case MultipartValueType(partCodecMetas, defaultCodecMeta) =>
//    }
    ???
  }

}
