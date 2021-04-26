package sttp.tapir

import sttp.tapir.model.SttpFile

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

object Defaults {
  def createTempFile: () => File = () => File.createTempFile("tapir", "tmp")
  def deleteFile(): SttpFile => Unit = file => file.toFile.delete()
}
