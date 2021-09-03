package sttp.tapir.static

import sttp.model.MediaType
import sttp.model.headers.ETag
import sttp.tapir.RangeValue

import java.time.Instant

case class StaticInput(
    path: List[String],
    ifNoneMatch: Option[List[ETag]],
    ifModifiedSince: Option[Instant],
    range: Option[RangeValue]
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
      acceptRanges: Option[String] = None,
      contentRange: Option[String] = None
  ) extends StaticOutput[T]
}