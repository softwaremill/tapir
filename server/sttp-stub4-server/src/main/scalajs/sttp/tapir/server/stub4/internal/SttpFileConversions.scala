package sttp.tapir.server.stub4.internal

import sttp.client4.internal.SttpFile
import sttp.tapir.{RangeValue, TapirFile}
import java.io.InputStream

/** Converts between sttp's and tapir's file representations, in both directions. `TapirFile` is platform-specific (`java.io.File` on the
  * JVM, `org.scalajs.dom.File` on JS), hence the platform-specific implementations.
  */
private[stub4] object SttpFileConversions {
  def toTapirFile(file: SttpFile): TapirFile = file.toDomFile

  // the same bounds as on the JVM decide whether the body is partial; only materializing it differs, as that would
  // require file-system access
  def toSttpFile(file: TapirFile, range: Option[RangeValue]): SttpFile = range.flatMap(_.startAndEnd) match {
    case None    => SttpFile.fromDomFile(file)
    case Some(_) => throw new UnsupportedOperationException("Ranged file responses are not supported on Scala.js")
  }

  def fileAsInputStream(file: SttpFile): InputStream = throw new UnsupportedOperationException(
    "InputStream-based body handling is not supported on Scala.js"
  )
}
