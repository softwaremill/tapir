package sttp.tapir.static

import sttp.model.MediaType
import sttp.model.headers.{ETag, Range}

import java.time.Instant

case class StaticInput(
    path: List[String],
    ifNoneMatch: Option[List[ETag]],
    ifModifiedSince: Option[Instant],
    range: Option[Range],
    acceptEncoding: Option[String]
)

case class HeadInput(
    path: List[String],
    acceptEncoding: Option[String]
)

trait StaticErrorOutput
object StaticErrorOutput {
  case object NotFound extends StaticErrorOutput
  case object BadRequest extends StaticErrorOutput
  case object RangeNotSatisfiable extends StaticErrorOutput
}

trait HeadOutput
object HeadOutput {
  case class Found(acceptRanges: Option[String], contentLength: Option[Long], contentType: Option[MediaType], contentEncoding: Option[String]) extends HeadOutput
}

trait StaticOutput[+T]
object StaticOutput {
  case object NotModified extends StaticOutput[Nothing]
  case class FoundPartial[T](
      body: T,
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[MediaType],
      etag: Option[ETag],
      acceptRanges: Option[String],
      contentRange: Option[String]
  ) extends StaticOutput[T]
  case class Found[T](
      body: T,
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[MediaType],
      etag: Option[ETag],
      contentEncoding: Option[String]
  ) extends StaticOutput[T]
}
