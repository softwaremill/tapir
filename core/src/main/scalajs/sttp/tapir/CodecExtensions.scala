package sttp.tapir

import org.scalajs.dom.File
import sttp.tapir.Codec.id
import sttp.tapir.CodecFormat.OctetStream
import sttp.tapir.DecodeResult.Value

trait CodecExtensions {
  implicit val jsFile: Codec[File, File, OctetStream] = id[File, OctetStream](OctetStream(), Schema.schemaForJSFile)
  implicit val tapirFile: Codec[FileRange, FileRange, OctetStream] =
    id[FileRange, OctetStream](OctetStream(), Schema.schemaForTapirFile)
  implicit val file: Codec[FileRange, File, OctetStream] = tapirFile.map(_.toFile)(FileRange.from)
}
