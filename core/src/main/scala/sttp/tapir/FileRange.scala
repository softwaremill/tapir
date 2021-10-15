package sttp.tapir

import sttp.model.ContentRangeUnits
import sttp.model.headers.ContentRange

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
