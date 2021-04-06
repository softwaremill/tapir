package sttp.tapir

import java.io.File

object Defaults {
  def createTempFile: () => File = () => {
    val f = File.createTempFile("tapir", "tmp")
    f.deleteOnExit()
    f
  }
}
