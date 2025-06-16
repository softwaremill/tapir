package sttp.tapir.server.stub4.internal

import sttp.client4.internal.SttpFile
import sttp.tapir.TapirFile
import java.io.InputStream
import java.io.FileInputStream

object SttpFileToTapirFile {
  def apply(file: SttpFile): TapirFile = file.toFile

  def fileAsInputStream(file: SttpFile): InputStream = new FileInputStream(file.toFile)
}
