package sttp.tapir

import java.nio.file.Path
import sttp.tapir.SchemaType.SBinary

trait SchemaExtensions {
  implicit val schemaForPath: Schema[Path] = Schema(SBinary())
  implicit val schemaForFile: Schema[File] = Schema(SBinary())
}
