package sttp.tapir.static

import sttp.model.MediaType
import sttp.model.headers.ETag

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

trait StaticOutput[+T]
object StaticOutput {
  case object NotModified extends StaticOutput[Nothing]
  case class Found[T](
      body: T,
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[MediaType],
      etag: Option[ETag]
  ) extends StaticOutput[T]
}
