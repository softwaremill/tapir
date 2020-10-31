package sttp.tapir

import java.io.File
import java.nio.file.Path

import sttp.tapir.Codec.id
import sttp.tapir.CodecFormat.OctetStream

trait CodecExtensions {
  implicit val file: Codec[File, File, OctetStream] = id[File, OctetStream](OctetStream(), Some(Schema.schemaForFile))
  implicit val path: Codec[File, Path, OctetStream] = file.map((_: File).toPath)(_.toFile)
}
