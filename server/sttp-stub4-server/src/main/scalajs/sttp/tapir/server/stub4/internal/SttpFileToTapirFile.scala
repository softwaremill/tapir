package sttp.tapir.server.stub4.internal

import sttp.client4.internal.SttpFile
import sttp.tapir.TapirFile
import java.io.InputStream

private[stub4] object SttpFileToTapirFile {
  def apply(file: SttpFile): TapirFile = file.toDomFile

  def fileAsInputStream(file: SttpFile): InputStream = throw new UnsupportedOperationException(
    "InputStream-based body handling is not supported on Scala.js"
  )
}
