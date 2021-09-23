package sttp.tapir

import sttp.tapir.Codec.id
import sttp.tapir.CodecFormat.OctetStream

import java.io.File
import java.nio.file.Path

trait CodecExtensions {
  implicit val fileRange: Codec[FileRange, FileRange, OctetStream] =
    id[FileRange, OctetStream](OctetStream(), Schema.schemaForTapirFile)
  implicit val file: Codec[FileRange, File, OctetStream] = fileRange.map(_.toFile)(FileRange.from)
  implicit val path: Codec[FileRange, Path, OctetStream] = file.map((_: File).toPath)(_.toFile)
}
