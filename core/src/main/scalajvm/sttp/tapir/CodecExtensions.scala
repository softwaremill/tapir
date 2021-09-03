package sttp.tapir

import sttp.tapir.Codec.{id, tapirFile}
import sttp.tapir.CodecFormat.OctetStream
import sttp.tapir.DecodeResult.Value
import sttp.tapir.internal.TapirFile

import java.io.File
import java.nio.file.Path

trait CodecExtensions {
  implicit val tapirFile: Codec[TapirFile, TapirFile, OctetStream] =
    id[TapirFile, OctetStream](OctetStream(), Schema.schemaForTapirFile)
  implicit val file: Codec[TapirFile, File, OctetStream] = tapirFile.map(_.toFile)(TapirFile.fromFile)
  implicit val path: Codec[TapirFile, Path, OctetStream] = file.map((_: File).toPath)(_.toFile)
  implicit val tapirFilePath: Codec[TapirFile, Path, OctetStream] =
    tapirFile.mapDecode(file => Value(file.toPath))(path => TapirFile.fromPath(path))
}
