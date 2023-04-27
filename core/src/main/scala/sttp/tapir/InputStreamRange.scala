package sttp.tapir

import java.io.InputStream

case class InputStreamRange(inputStream: () => InputStream, range: Option[RangeValue] = None) {
  def inputStreamFromRangeStart: () => InputStream = range.flatMap(_.start) match {
    case Some(start) if start > 0 =>
      () =>
        val openedStream = inputStream()
        val skipped = openedStream.skip(start)
        if (skipped == start)
          openedStream
        else
          throw new IllegalArgumentException(s"Illegal range start: $start, could skip only $skipped bytes")
    case _ => inputStream
  }
}
