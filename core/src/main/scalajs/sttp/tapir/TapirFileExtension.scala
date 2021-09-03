package sttp.tapir

import org.scalajs.dom.File
import sttp.tapir.internal.TapirFile

trait TapirFileExtension { self: TapirFile =>
  def toFile: File = underlying.asInstanceOf[File]
}

trait TapirFileCompanionExtensions {
  def fromFile(file: File): TapirFile = new TapirFile(file)
}
