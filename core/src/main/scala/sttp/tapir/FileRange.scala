package sttp.tapir

import sttp.model.ContentRangeUnits
import sttp.model.headers.ContentRange

import java.io.InputStream
import java.net.URL

case class FileRange(file: TapirFile, range: Option[RangeValue] = None)

case class RangeValue(start: Option[Long], end: Option[Long], fileSize: Long) {
  def toContentRange: ContentRange =
    ContentRange(ContentRangeUnits.Bytes, start.zip(end).headOption, Some(fileSize))

  def contentLength: Long = (start, end) match {
    case (Some(_start), Some(_end)) => _end - _start + 1
    case (Some(_start), None)       => fileSize - _start
    case (None, Some(_end))         => _end
    case _                          => 0
  }

  val startAndEnd: Option[(Long, Long)] = (start, end) match {
    case (Some(_start), Some(_end)) => Some((_start, _end + 1))
    case (Some(_start), None)       => Some((_start, fileSize))
    case (None, Some(_end))         => Some((fileSize - _end, fileSize))
    case _                          => None
  }
}

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
