package sttp.tapir

import java.io.File

object Defaults {
  val Prefix: String = "tapir"
  def createTempFile: () => TapirFile = () => File.createTempFile(Prefix, "tmp")
  def deleteFile(): TapirFile => Unit = file => {
    val _ = file.delete()
  }
}
