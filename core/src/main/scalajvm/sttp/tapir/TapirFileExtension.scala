package sttp.tapir

import java.nio.file.Path
import sttp.tapir.internal.TapirFile

trait TapirFileExtension { self: TapirFile =>
  def toPath: Path = underlying.asInstanceOf[Path]
  def toFile: java.io.File = toPath.toFile
}

trait TapirFileCompanionExtensions {
  def fromPath(path: Path): TapirFile = new TapirFile(path)
  def fromFile(file: java.io.File): TapirFile = fromPath(file.toPath)
}
