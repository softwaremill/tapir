package sttp.tapir

import sttp.tapir.model.SttpFile

import java.io.File
import scala.concurrent.Future

object Defaults {
  def createTempFile: () => File = () => File.createTempFile("tapir", "tmp")

  def deleteFiles(): Seq[SttpFile] => Future[Unit] = { files =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(files.foreach(_.toFile.delete()))
  }
}
