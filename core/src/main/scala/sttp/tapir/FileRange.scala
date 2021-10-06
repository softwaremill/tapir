package sttp.tapir

import sttp.model.ContentRangeUnits
import sttp.model.headers.ContentRange

case class FileRange(file: TapirFile, range: Option[RangeValue] = None)

case class RangeValue(start: Option[Long], end: Option[Long], fileSize: Long) {
  def toContentRange: ContentRange =
    ContentRange(ContentRangeUnits.Bytes, start.zip(end).headOption, Some(fileSize))

  def contentLength: Long = (start, end) match {
    case (Some(_start), Some(_end)) => _end - _start + 1
    case (Some(_start), None)       => fileSize - _start + 1
    case (None, Some(_end))         => _end + 1
    case _                          => 0
  }
}
