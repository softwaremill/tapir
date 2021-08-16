package sttp.tapir.static

import sttp.model.MediaType
import sttp.model.headers.ETag

import java.io.File
import java.time.Instant

case class StaticInput(
    path: List[String],
    ifNoneMatch: Option[List[ETag]],
    ifModifiedSince: Option[Instant]
)

trait StaticErrorOutput
object StaticErrorOutput {
  case object NotFound extends StaticErrorOutput
  case object BadRequest extends StaticErrorOutput
}

trait StaticOutput
object StaticOutput {
  case object NotModified extends StaticOutput
  case class Found(
      body: File,
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[MediaType],
      etag: Option[ETag]
  ) extends StaticOutput
}
