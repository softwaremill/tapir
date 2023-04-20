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
    acceptEncoding: Option[String]
) {

  def acceptGzip: Boolean = acceptEncoding.contains("gzip")
}

trait StaticErrorOutput
object StaticErrorOutput {
  case object NotFound extends StaticErrorOutput
  case object BadRequest extends StaticErrorOutput
  case object RangeNotSatisfiable extends StaticErrorOutput
}

trait HeadOutput
object HeadOutput {
  case object NotModified extends HeadOutput
  case class FoundPartial(
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[MediaType],
      etag: Option[ETag],
      acceptRanges: Option[String],
      contentRange: Option[String]
  ) extends HeadOutput
  case class Found(
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[MediaType],
      etag: Option[ETag],
      acceptRanges: Option[String],
      contentEncoding: Option[String]
  ) extends HeadOutput

  def fromStaticOutput(output: StaticOutput[_]): HeadOutput =
    (output: @unchecked) match {
      case StaticOutput.FoundPartial(_, lastModified, contentLength, contentType, etag, acceptRanges, contentEncoding) =>
        HeadOutput.FoundPartial(lastModified, contentLength, contentType, etag, acceptRanges, contentEncoding)
      case StaticOutput.Found(_, lastModified, contentLength, contentType, etag, acceptRanges, contentEncoding) =>
        HeadOutput.Found(lastModified, contentLength, contentType, etag, acceptRanges, contentEncoding)
      case StaticOutput.NotModified =>
        HeadOutput.NotModified
    }
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
      acceptRanges: Option[String],
      contentEncoding: Option[String]
  ) extends StaticOutput[T]
}
