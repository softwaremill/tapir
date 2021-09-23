package sttp.tapir

import java.io.File

object Defaults {
  def createTempFile: () => File = () => File.createTempFile("tapir", "tmp")
  def deleteFile(): File => Unit = file => file.delete()
}
