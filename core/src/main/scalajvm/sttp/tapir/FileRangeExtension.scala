package sttp.tapir

trait FileRangeExtension { self: FileRange =>
  def toFile: java.io.File = underlying.asInstanceOf[File]
}

trait FileRangeCompanionExtensions {
  def from(file: java.io.File): FileRange = FileRange(file)
  def from(file: java.io.File, range: RangeValue): FileRange = new FileRange(file.toPath, Some(range))
}
