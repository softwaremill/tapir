package sttp.tapir

import org.scalajs.dom.File

import sttp.tapir.Codec.id
import sttp.tapir.CodecFormat.OctetStream

trait CodecExtensions {
  implicit val file: Codec[File, File, OctetStream] = id[File, OctetStream](OctetStream(), Schema.schemaForJSFile)
}
