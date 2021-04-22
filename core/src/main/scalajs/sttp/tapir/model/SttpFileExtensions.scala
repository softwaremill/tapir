package sttp.tapir.model

import org.scalajs.dom.File

trait SttpFileExtensions { self: SttpFile =>

  def toDomFile: File = underlying.asInstanceOf[File]

  def readAsString: String = throw new UnsupportedOperationException()
  def readAsByteArray: Array[Byte] = throw new UnsupportedOperationException()
}

trait SttpFileCompanionExtensions {
  def fromDomFile(file: File): SttpFile =
    new SttpFile(file) {
      val name: String = file.name
      val size: Long = file.size.toLong
    }
}
