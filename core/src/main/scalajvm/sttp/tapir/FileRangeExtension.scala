package sttp.tapir

import java.nio.file.Path
import sttp.model.headers.Range

trait FileRangeExtension { self: FileRange =>
  def toPath: Path = underlying.asInstanceOf[Path]
  def toFile: java.io.File = underlying.asInstanceOf[File]
}

trait FileRangeCompanionExtensions {
  def from(file: java.io.File): FileRange = FileRange(file)
  def from(file: java.io.File, range: Range): FileRange = new FileRange(file.toPath, Some(range))
}
