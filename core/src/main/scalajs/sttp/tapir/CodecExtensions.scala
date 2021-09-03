package sttp.tapir

import org.scalajs.dom.File
import sttp.tapir.Codec.id
import sttp.tapir.CodecFormat.OctetStream
import sttp.tapir.DecodeResult.Value
import sttp.tapir.internal.TapirFile

trait CodecExtensions {
  implicit val jsFile: Codec[File, File, OctetStream] = id[File, OctetStream](OctetStream(), Schema.schemaForJSFile)
  implicit val tapirFile: Codec[TapirFile, TapirFile, OctetStream] =
    id[TapirFile, OctetStream](OctetStream(), Schema.schemaForTapirFile)
  implicit val file: Codec[TapirFile, File, OctetStream] = tapirFile.map(_.toFile)(TapirFile.fromFile)
}
