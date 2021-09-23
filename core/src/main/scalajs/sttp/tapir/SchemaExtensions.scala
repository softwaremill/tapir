package sttp.tapir

import sttp.tapir.SchemaType.SBinary

trait SchemaExtensions {
  implicit val schemaForJSFile: Schema[File] = Schema(SBinary())
}
