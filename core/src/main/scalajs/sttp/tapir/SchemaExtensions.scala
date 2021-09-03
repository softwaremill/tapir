package sttp.tapir

import sttp.tapir.SchemaType.SBinary
import org.scalajs.dom.File

trait SchemaExtensions {
  implicit val schemaForJSFile: Schema[File] = Schema(SBinary())
}
