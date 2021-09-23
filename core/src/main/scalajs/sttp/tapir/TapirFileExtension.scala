package sttp.tapir

import org.scalajs.dom.File

trait FileRangeExtension { self: FileRange =>
  def toFile: File = underlying.asInstanceOf[File]
}

trait FileRangeCompanionExtensions {
  def from(file: File): FileRange = new FileRange(file)
}
