package sttp.tapir

import sttp.model.ContentRangeUnits
import sttp.model.headers.ContentRange

case class FileRange(file: TapirFile, range: Option[RangeValue] = None)

case class RangeValue(start: Long, end: Long) {

  def toContentRange(fileSize: Long): ContentRange =
    ContentRange(ContentRangeUnits.Bytes, Some((start, end)), Some(fileSize))

  val contentLength: Long = end - start

  def isValid(contentSize: Long): Boolean = end < contentSize
}