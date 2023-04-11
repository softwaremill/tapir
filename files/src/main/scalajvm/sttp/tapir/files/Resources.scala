package sttp.tapir.files

import sttp.model.MediaType
import sttp.model.headers.ETag
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.File
import java.time.Instant
import java.io.FileNotFoundException
import sttp.tapir.ResourceRange
import sttp.tapir.RangeValue
import java.net.URL

import sttp.model.ContentRangeUnits

object Resources {

  def head[F[_]](
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => HeadInput => F[Either[StaticErrorOutput, HeadOutput]] = implicit monad =>
    input => {
      MonadError[F]
        .blocking {
          if (!options.fileFilter(input.path)) {
            Left(StaticErrorOutput.NotFound)
          } else {
            resolveURL(classLoader, resourcePrefix.split("/").toList, input.path, options.defaultFile, useGzippedIfAvailable = false) match {

              case None => Left(StaticErrorOutput.NotFound)
              case Some((resolvedUrl, _, _)) =>
                val conn = resolvedUrl.openConnection()

                Right(
                  HeadOutput.Found(
                    Some(ContentRangeUnits.Bytes),
                    Some(conn.getContentLengthLong()),
                    Some(contentTypeFromName(resolvedUrl.getPath))
                  )
                )
            }
          }
        }
    }

  def apply[F[_]: MonadError](
      classLoader: ClassLoader,
      resourcePrefix: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): StaticInput => F[Either[StaticErrorOutput, StaticOutput[ResourceRange]]] = (resourcesInput: StaticInput) =>
    resources(classLoader, resourcePrefix.split("/").toList, options)(resourcesInput)

  private def resources[F[_]](
      classLoader: ClassLoader,
      resourcePrefix: List[String],
      options: FilesOptions[F]
  )(
      resourcesInput: StaticInput
  )(implicit
      m: MonadError[F]
  ): F[Either[StaticErrorOutput, StaticOutput[ResourceRange]]] = {
    def err(errorObj: StaticErrorOutput): F[Either[StaticErrorOutput, StaticOutput[ResourceRange]]] =
      (Left(errorObj): Either[StaticErrorOutput, StaticOutput[ResourceRange]]).unit

    val nameComponents = resourcePrefix ++ resourcesInput.path
    if (options.fileFilter(nameComponents)) {
      val useGzippedIfAvailable =
        resourcesInput.range.isEmpty && options.useGzippedIfAvailable && resourcesInput.acceptEncoding.exists(_.equals("gzip"))
      m.suspend {
        resolveURL(classLoader, resourcePrefix, resourcesInput.path, options.defaultFile, useGzippedIfAvailable) match {
          case Some((url, mt, enc)) =>
            resourcesInput.range match {
              case Some(range) =>
                val fileSize = url.openConnection().getContentLengthLong()
                if (range.isValid(fileSize)) {
                  val rangeValue = RangeValue(range.start, range.end, fileSize)
                  readResourceRange(options.calculateETag(m)(Some(rangeValue)), resourcesInput, url, mt, enc, rangeValue)
                    .map(Right(_): Either[StaticErrorOutput, StaticOutput[ResourceRange]])
                } else err(StaticErrorOutput.RangeNotSatisfiable)
              case None =>
                readResourceFull(options.calculateETag(m)(None), resourcesInput, url, mt, enc)
                  .map(Right(_): Either[StaticErrorOutput, StaticOutput[ResourceRange]])
            }
          case None => err(StaticErrorOutput.NotFound)
        }
      }.handleError { case _: FileNotFoundException =>
        err(StaticErrorOutput.NotFound)
      }
    } else err(StaticErrorOutput.NotFound)
  }

  private def resolveURL(
      classLoader: ClassLoader,
      resourcePrefix: List[String],
      path: List[String],
      default: Option[List[String]],
      useGzippedIfAvailable: Boolean
  ): Option[(URL, MediaType, Option[String])] = {
    val name = (resourcePrefix ++ path).mkString("/")

    val result = (if (useGzippedIfAvailable) Option(classLoader.getResource(name + ".gz")).map((_, MediaType.ApplicationGzip, Some("gzip")))
                  else None)
      .orElse(Option(classLoader.getResource(name)).map((_, contentTypeFromName(name), None)))
      .orElse(default match {
        case None              => None
        case Some(defaultPath) => resolveURL(classLoader, resourcePrefix, defaultPath, None, useGzippedIfAvailable)
      })
      // making sure that the resulting path contains the original requested path
      .filter(_._1.toURI.toString.contains(resourcePrefix.mkString("/")))

    if (result.exists(r => isDirectory(classLoader, name, r._1)))
      resolveURL(classLoader, resourcePrefix, path :+ "index.html", default, useGzippedIfAvailable)
    else result
  }

  private def isDirectory(classLoader: ClassLoader, name: String, nameResource: URL): Boolean = {
    // https://stackoverflow.com/questions/20105554/is-there-a-way-to-tell-if-a-classpath-resource-is-a-file-or-a-directory
    if (nameResource.getProtocol == "file") new File(nameResource.getPath).isDirectory
    else classLoader.getResource(name + "/") != null
  }

  private def resourceOutput[F[_]](
      resourcesInput: StaticInput,
      url: URL,
      calculateETag: URL => F[Option[ETag]],
      result: (Long, Long, Option[ETag]) => StaticOutput[ResourceRange]
  )(implicit
      m: MonadError[F]
  ): F[StaticOutput[ResourceRange]] =
    for {
      etagOpt <- calculateETag(url)
      urlConnection <- m.blocking(url.openConnection())
      lastModified <- m.blocking(urlConnection.getLastModified())
      resourceResult <-
        if (isModified(resourcesInput, etagOpt, lastModified))
          m.blocking(urlConnection.getContentLengthLong).map(fileLength => result(lastModified, fileLength, etagOpt))
        else StaticOutput.NotModified.unit
    } yield resourceResult

  private def readResourceRange[F[_]](
      calculateETag: URL => F[Option[ETag]],
      resourcesInput: StaticInput,
      url: URL,
      contentType: MediaType,
      contentEncoding: Option[String],
      range: RangeValue
  )(implicit m: MonadError[F]): F[StaticOutput[ResourceRange]] =
    resourceOutput(
      resourcesInput,
      url,
      calculateETag,
      (lastModified, fileLength, etag) =>
        StaticOutput
          .Found(
            ResourceRange(url, Some(range)),
            Some(Instant.ofEpochMilli(lastModified)),
            Some(fileLength),
            Some(contentType),
            etag,
            contentEncoding
          )
    )

  private def readResourceFull[F[_]](
      calculateETag: URL => F[Option[ETag]],
      resourcesInput: StaticInput,
      url: URL,
      contentType: MediaType,
      contentEncoding: Option[String]
  )(implicit m: MonadError[F]): F[StaticOutput[ResourceRange]] =
    resourceOutput(
      resourcesInput,
      url,
      calculateETag,
      (lastModified, fileLength, etag) =>
        StaticOutput
          .Found(
            ResourceRange(url, None),
            Some(Instant.ofEpochMilli(lastModified)),
            Some(fileLength),
            Some(contentType),
            etag,
            contentEncoding
          )
    )

}
