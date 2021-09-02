package sttp.tapir

import sttp.tapir.SchemaType.SBinary

trait SchemaExtensions {
  implicit val schemaForJSFile: Schema[org.scalajs.dom.File] = Schema(SBinary())
}
