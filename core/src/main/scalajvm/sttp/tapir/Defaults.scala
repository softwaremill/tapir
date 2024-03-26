package sttp.tapir

import java.io.File

object Defaults {
  def createTempFile: () => TapirFile = () => File.createTempFile("tapir", "tmp")
  def deleteFile(): TapirFile => Unit = file => {
    val _ = file.delete()
  }
}
