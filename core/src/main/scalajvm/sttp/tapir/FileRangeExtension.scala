package sttp.tapir

import java.nio.file.Path

trait FileRangeExtension { self: FileRange =>
  def toPath: Path = underlying.asInstanceOf[Path]
  def toFile: java.io.File = underlying.asInstanceOf[File]
}

trait FileRangeCompanionExtensions {
  def from(file: java.io.File): FileRange = FileRange(file)
  def from(file: java.io.File, range: RangeValue): FileRange = new FileRange(file.toPath, Some(range))
}
