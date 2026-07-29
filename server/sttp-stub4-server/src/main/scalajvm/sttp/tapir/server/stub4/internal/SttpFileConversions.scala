package sttp.tapir.server.stub4.internal

import sttp.client4.internal.SttpFile
import sttp.tapir.TapirFile
import java.io.InputStream
import java.io.FileInputStream

/** Converts between sttp's and tapir's file representations, in both directions. `TapirFile` is platform-specific (`java.io.File` on the
  * JVM, `org.scalajs.dom.File` on JS), hence the platform-specific implementations.
  */
private[stub4] object SttpFileConversions {
  def toTapirFile(file: SttpFile): TapirFile = file.toFile

  def toSttpFile(file: TapirFile): SttpFile = SttpFile.fromFile(file)

  def fileAsInputStream(file: SttpFile): InputStream = new FileInputStream(file.toFile)
}
