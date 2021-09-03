package sttp.tapir

import sttp.tapir.internal.TapirFile

import java.io.File

object Defaults {
  def createTempFile: () => TapirFile = () => TapirFile.fromFile(File.createTempFile("tapir", "tmp"))
  def deleteFile(): TapirFile => Unit = file => file.toFile.delete()
}
