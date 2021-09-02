package sttp.tapir.static

import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.{FileNotFoundException, InputStream}
import java.time.Instant

object Resources {
  def apply[F[_]: MonadError](
      classLoader: ClassLoader,
      resourcePrefix: String,
      useETags: Boolean = true
  ): StaticInput => F[Either[StaticErrorOutput, StaticOutput[InputStream]]] = (resourcesInput: StaticInput) =>
    resources(classLoader, resourcePrefix.split("/").toList, useETags)(resourcesInput)

  private def resources[F[_]](classLoader: ClassLoader, resourcePrefix: List[String], useETags: Boolean)(resourcesInput: StaticInput)(
      implicit m: MonadError[F]
  ): F[Either[StaticErrorOutput, StaticOutput[InputStream]]] = {
    val name = (resourcePrefix ++ resourcesInput.path).mkString("/")
    m.blocking {
      val res = classLoader.getResource(name)

      if (res == null) {
        Left(StaticErrorOutput.NotFound)
      } else {
        val conn = res.openConnection()

        val lastModified = conn.getLastModified
        val length = conn.getContentLengthLong

        val etag = if (useETags) Some(defaultETag(lastModified, length)) else None

        if (isModified(resourcesInput, etag, lastModified)) {
          val contentType = contentTypeFromName(name)
          Right(
            StaticOutput.Found(conn.getInputStream, Some(Instant.ofEpochMilli(lastModified)), Some(length), Some(contentType), etag, Some(""), Some("a"))
          ): Either[StaticErrorOutput, StaticOutput[InputStream]]
        } else Right(StaticOutput.NotModified)
      }
    }.handleError { case _: FileNotFoundException =>
      (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[InputStream]]).unit
    }
  }
}
