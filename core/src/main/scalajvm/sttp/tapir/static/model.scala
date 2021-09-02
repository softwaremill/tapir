package sttp.tapir.static

import sttp.model.{Header, MediaType}
import sttp.model.headers.ETag

import java.time.Instant
import scala.util.Try

case class StaticInput(
    path: List[String],
    ifNoneMatch: Option[List[ETag]],
    ifModifiedSince: Option[Instant],
    range: Option[RangeValue] = Some(RangeValue("a", 1, 1))
)

trait StaticErrorOutput
object StaticErrorOutput {
  case object NotFound extends StaticErrorOutput
  case object BadRequest extends StaticErrorOutput
  case object RangeNotSatisfiable extends StaticErrorOutput
}

trait StaticOutput[+T]
object StaticOutput {
  case object NotModified extends StaticOutput[Nothing]
  case class Found[T](
      body: T,
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[MediaType],
      etag: Option[ETag],
      acceptRanges: Option[String],
      contentRange: Option[String]
  ) extends StaticOutput[T]
}

case class RangeValue(unit: String, start: Int, end: Int)

object RangeValue {

  def fromString(str: String): Either[String, RangeValue] = {
    Try({
      val splited = str.split("=")
      val unit = splited(0)
      val range = splited(1).split("-")
      // TODO add support for other cases of range
      RangeValue(unit, range(0).toInt, range(1).toInt)
    })
      .toEither
      .left
      .map(_.getMessage)
  }
}