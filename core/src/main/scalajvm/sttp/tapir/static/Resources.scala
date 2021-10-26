package sttp.tapir.static

import sttp.model.MediaType
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.{FileNotFoundException, InputStream}
import java.net.URL
import java.time.Instant

object Resources {
  def apply[F[_]: MonadError](
      classLoader: ClassLoader,
      resourcePrefix: String,
      useETags: Boolean = true,
      useGzippedIfAvailable: Boolean = false
  ): StaticInput => F[Either[StaticErrorOutput, StaticOutput[InputStream]]] = (resourcesInput: StaticInput) =>
    resources(classLoader, resourcePrefix.split("/").toList, useETags, useGzippedIfAvailable)(resourcesInput)

  private def resources[F[_]](classLoader: ClassLoader, resourcePrefix: List[String], useETags: Boolean, useGzippedIfAvailable: Boolean)(
      resourcesInput: StaticInput
  )(implicit
      m: MonadError[F]
  ): F[Either[StaticErrorOutput, StaticOutput[InputStream]]] = {
    val gzippedResource = useGzippedIfAvailable && resourcesInput.acceptEncoding.exists(_.equals("gzip"))
    val name = (resourcePrefix ++ resourcesInput.path).mkString("/")

    val gzipUrl: F[Option[URL]] =
      if (gzippedResource) m.blocking(Option(classLoader.getResource(name.concat(".gz"))))
      else m.unit(Option.empty)

    gzipUrl
      .flatMap(maybeUrl =>
        m.blocking(
          maybeUrl
            .map(url => readResource(useETags, resourcesInput, url, Some(MediaType.ApplicationGzip), Some("gzip")))
            .getOrElse(
              Option(classLoader.getResource(name))
                .map(url => readResource(useETags, resourcesInput, url, Some(contentTypeFromName(name)), None))
                .getOrElse(Left(StaticErrorOutput.NotFound))
            )
        )
      )
      .handleError { case _: FileNotFoundException =>
        (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[InputStream]]).unit
      }
  }

  private def readResource[F[_]](
      useETags: Boolean,
      resourcesInput: StaticInput,
      url: URL,
      contentType: Option[MediaType],
      contentEncoding: Option[String]
  ): Either[StaticErrorOutput, StaticOutput[InputStream]] = {
    val conn = url.openConnection()

    val lastModified = conn.getLastModified
    val length = conn.getContentLengthLong

    val etag = if (useETags) Some(defaultETag(lastModified, length)) else None

    if (isModified(resourcesInput, etag, lastModified))
      Right(
        StaticOutput.Found(conn.getInputStream, Some(Instant.ofEpochMilli(lastModified)), Some(length), contentType, etag, contentEncoding)
      )
    else Right(StaticOutput.NotModified)
  }

}
