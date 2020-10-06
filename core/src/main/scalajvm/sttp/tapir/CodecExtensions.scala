package sttp.tapir

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Path

import sttp.tapir.Codec.id
import sttp.tapir.CodecFormat.OctetStream

trait CodecExtensions {
  implicit val file: Codec[File, File, OctetStream] = id[File, OctetStream](OctetStream(), Some(Schema(SchemaType.SBinary)))
  implicit val path: Codec[File, Path, OctetStream] = file.map((_: File).toPath)(_.toFile)
}

/** The raw format of the body: what do we need to know, to read it and pass to a codec for further decoding.
  */
sealed trait RawBodyType[R]
object RawBodyType {
  case class StringBody(charset: Charset) extends RawBodyType[String]

  sealed trait Binary[R] extends RawBodyType[R]
  implicit case object ByteArrayBody extends Binary[Array[Byte]]
  implicit case object ByteBufferBody extends Binary[ByteBuffer]
  implicit case object InputStreamBody extends Binary[InputStream]
  implicit case object FileBody extends Binary[File]

  case class MultipartBody(partTypes: Map[String, RawBodyType[_]], defaultType: Option[RawBodyType[_]]) extends RawBodyType[Seq[RawPart]] {
    def partType(name: String): Option[RawBodyType[_]] = partTypes.get(name).orElse(defaultType)
  }
}
