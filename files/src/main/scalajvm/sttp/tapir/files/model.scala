package sttp.tapir.files

import sttp.model.MediaType
import sttp.model.headers.{ETag, Range}

import java.net.URL
import java.time.Instant

private[tapir] case class ResolvedUrl(url: URL, mediaType: MediaType, contentEncoding: Option[String])

case class StaticInput(
    path: List[String],
    ifNoneMatch: Option[List[ETag]],
    ifModifiedSince: Option[Instant],
    range: Option[Range],
    acceptEncoding: List[String]
) {
  def acceptGzip: Boolean = acceptEncoding.exists(_.contains("gzip"))
}

trait StaticErrorOutput
object StaticErrorOutput {
  case object NotFound extends StaticErrorOutput
  case object BadRequest extends StaticErrorOutput
  case object RangeNotSatisfiable extends StaticErrorOutput
}

sealed trait StaticOutput[+T] {
  def withoutBody: StaticOutput[Unit] =
    this match {
      case StaticOutput.NotModified        => StaticOutput.NotModified
      case o: StaticOutput.FoundPartial[T] =>
        o.copy(body = ())
      case o: StaticOutput.Found[T] =>
        o.copy(body = ())
    }
}

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
      acceptRanges: Option[String],
      contentEncoding: Option[String]
  ) extends StaticOutput[T]
}
