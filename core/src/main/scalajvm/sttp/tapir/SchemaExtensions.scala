package sttp.tapir

import java.nio.file.Path
import sttp.tapir.SchemaType.SBinary

import java.io.File

trait SchemaExtensions {
  implicit val schemaForPath: Schema[Path] = Schema(SBinary())
  implicit val schemaForNormalFile: Schema[File] = Schema(SBinary())
}
