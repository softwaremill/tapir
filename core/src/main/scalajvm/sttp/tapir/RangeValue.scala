package sttp.tapir

import scala.util.Try

case class RangeValue(unit: String, start: Int, end: Int) {
  // TODO make it better
  def validate(contentLength: Long): Boolean = if (end < contentLength) true else false

  def toContentRange(fileSize: Long): String = unit + " " + start + "-" + end + "/" + fileSize
}

object RangeValue {

  def fromString(str: String): Either[String, RangeValue] = {
    Try({
      val splited = str.split("=")
      val unit = splited(0)
      val range = splited(1).split("-")
      // TODO add support for other cases of range
      RangeValue(unit, range(0).toInt, range(1).toInt)
    }).toEither.left
      .map(_.getMessage)
  }
}