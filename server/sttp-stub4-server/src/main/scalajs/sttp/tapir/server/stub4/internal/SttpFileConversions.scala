package sttp.tapir.server.stub4.internal

import sttp.client4.internal.SttpFile
import sttp.tapir.TapirFile
import java.io.InputStream

/** Converts between sttp's and tapir's file representations, in both directions. `TapirFile` is platform-specific (`java.io.File` on the
  * JVM, `org.scalajs.dom.File` on JS), hence the platform-specific implementations.
  */
private[stub4] object SttpFileConversions {
  def toTapirFile(file: SttpFile): TapirFile = file.toDomFile

  def toSttpFile(file: TapirFile): SttpFile = SttpFile.fromDomFile(file)

  def fileAsInputStream(file: SttpFile): InputStream = throw new UnsupportedOperationException(
    "InputStream-based body handling is not supported on Scala.js"
  )
}
