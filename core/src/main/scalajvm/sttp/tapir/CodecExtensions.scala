package sttp.tapir

import sttp.tapir.Codec.fileRange
import sttp.tapir.CodecFormat.OctetStream

import java.nio.file.Path

trait CodecExtensions {
  implicit lazy val path: Codec[FileRange, Path, OctetStream] = fileRange.map(d => d.file.toPath)(e => FileRange(e.toFile))
}
