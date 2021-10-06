package sttp.tapir

import sttp.tapir.CodecFormat.OctetStream

import java.nio.file.Path

trait CodecExtensions {
  implicit val path: Codec[FileRange, Path, OctetStream] = Codec.file.map(_.toPath)(_.toFile)
}
