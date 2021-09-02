package sttp.tapir

import java.nio.file.Path

case class TapirFile(underlying: Any, range: Option[RangeValue] = None) {
  def toPath: Path = underlying.asInstanceOf[Path]
  def toFile: java.io.File = toPath.toFile

}

object TapirFile {
  def fromPath(path: Path): TapirFile = TapirFile(path)
  def fromFile(file: java.io.File): TapirFile = fromPath(file.toPath)
}
