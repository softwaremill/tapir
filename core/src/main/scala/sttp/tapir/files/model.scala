package sttp.tapir.files

import sttp.model.headers.ETag

import java.io.{File, InputStream}
import java.time.Instant

case class FilesInput(
    path: List[String],
    ifNoneMatch: Option[List[ETag]],
    ifModifiedSince: Option[Instant]
)

trait FilesErrorOutput
object FilesErrorOutput {
  case object NotFound extends FilesErrorOutput
  case object BadRequest extends FilesErrorOutput
}

trait FilesOutput
object FilesOutput {
  case object NotModified extends FilesOutput
  case class Found(
      body: Either[File, InputStream],
      lastModified: Option[Instant],
      contentLength: Option[Long],
      contentType: Option[String],
      etag: Option[ETag]
  ) extends FilesOutput
}
