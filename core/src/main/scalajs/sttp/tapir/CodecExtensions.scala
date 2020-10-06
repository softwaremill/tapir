package sttp.tapir

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

trait CodecExtensions

/** The raw format of the body: what do we need to know, to read it and pass to a codec for further decoding.
  */
sealed trait RawBodyType[R]
object RawBodyType {
  case class StringBody(charset: Charset) extends RawBodyType[String]

  sealed trait Binary[R] extends RawBodyType[R]
  implicit case object ByteArrayBody extends Binary[Array[Byte]]
  implicit case object ByteBufferBody extends Binary[ByteBuffer]
  implicit case object InputStreamBody extends Binary[InputStream]

  case class MultipartBody(partTypes: Map[String, RawBodyType[_]], defaultType: Option[RawBodyType[_]]) extends RawBodyType[Seq[RawPart]] {
    def partType(name: String): Option[RawBodyType[_]] = partTypes.get(name).orElse(defaultType)
  }
}
