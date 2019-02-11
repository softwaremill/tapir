package tapir

import java.io.File

object Defaults {
  def createTempFile: () => File = () => File.createTempFile("tapir", "tmp")
}
