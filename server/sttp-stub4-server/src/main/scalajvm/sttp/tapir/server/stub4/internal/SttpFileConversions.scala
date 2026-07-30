package sttp.tapir.server.stub4.internal

import sttp.client4.internal.SttpFile
import sttp.tapir.{RangeValue, TapirFile}
import java.io.InputStream
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.annotation.tailrec

/** Converts between sttp's and tapir's file representations, in both directions. `TapirFile` is platform-specific (`java.io.File` on the
  * JVM, `org.scalajs.dom.File` on JS), hence the platform-specific implementations.
  */
private[stub4] object SttpFileConversions {
  def toTapirFile(file: SttpFile): TapirFile = file.toFile

  /** For a non-empty range, the selected part of the file is copied to a temporary file: sttp's stub can serve a file response only from a
    * whole `SttpFile`, so a partial body has to be materialized. The `start`/`end` bounds are the ones used by the server interpreters.
    */
  def toSttpFile(file: TapirFile, range: Option[RangeValue]): SttpFile = range.flatMap(_.startAndEnd) match {
    case Some((start, end)) =>
      val ranged = Files.createTempFile("tapir-stub-range", ".tmp")
      ranged.toFile.deleteOnExit()
      copyRange(file, start, end, ranged)
      SttpFile.fromPath(ranged)
    case None => SttpFile.fromFile(file)
  }

  def fileAsInputStream(file: SttpFile): InputStream = new FileInputStream(file.toFile)

  private def copyRange(file: TapirFile, start: Long, end: Long, to: Path): Unit = {
    val from = FileChannel.open(file.toPath, StandardOpenOption.READ)
    try {
      val target = FileChannel.open(to, StandardOpenOption.WRITE)
      try {
        // a single `transferTo` may copy less than requested
        @tailrec def transfer(position: Long): Unit =
          if (position < end) {
            val transferred = from.transferTo(position, end - position, target)
            if (transferred > 0) transfer(position + transferred)
          }

        transfer(start)
      } finally target.close()
    } finally from.close()
  }
}
