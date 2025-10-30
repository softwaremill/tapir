package sttp.tapir.static

import sttp.model.ContentRangeUnits
import sttp.model.headers.ETag
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{FileRange, RangeValue}

import java.io.File
import java.nio.file.{LinkOption, Path, Paths}
import java.time.Instant
import scala.annotation.tailrec

@deprecated("Use sttp.tapir.files.Files", since = "1.3.0")
object Files {

  // inspired by org.http4s.server.staticcontent.FileService
  def head[F[_]](
      systemPath: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => HeadInput => F[Either[StaticErrorOutput, HeadOutput]] = implicit monad =>
    input => {
      MonadError[F]
        .blocking {
          if (!options.fileFilter(input.path)) {
            Left(StaticErrorOutput.NotFound)
          } else {
            resolveRealPath(Paths.get(systemPath).toRealPath(), input.path, options.defaultFile) match {
              case Left(error)     => Left(error)
              case Right(resolved) =>
                val file = resolved.toFile
                Right(HeadOutput.Found(Some(ContentRangeUnits.Bytes), Some(file.length()), Some(contentTypeFromName(file.getName))))
            }
          }
        }
    }

  def get[F[_]](
      systemPath: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[FileRange]]] = { implicit monad => filesInput =>
    MonadError[F].blocking(Paths.get(systemPath).toRealPath()).flatMap(path => files(path, options)(filesInput))
  }

  def defaultEtag[F[_]]: MonadError[F] => File => F[Option[ETag]] = monad =>
    file =>
      monad.blocking {
        if (file.isFile) Some(defaultETag(file.lastModified(), file.length()))
        else None
      }

  private def files[F[_]](realSystemPath: Path, options: FilesOptions[F])(
      filesInput: StaticInput
  )(implicit
      m: MonadError[F]
  ): F[Either[StaticErrorOutput, StaticOutput[FileRange]]] = {
    m.flatten(m.blocking {
      if (!options.fileFilter(filesInput.path))
        (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[FileRange]]).unit
      else {
        resolveRealPath(realSystemPath, filesInput.path, options.defaultFile) match {
          case Left(error)             => (Left(error): Either[StaticErrorOutput, StaticOutput[FileRange]]).unit
          case Right(realResolvedPath) =>
            filesInput.range match {
              case Some(range) =>
                val fileSize = realResolvedPath.toFile.length()
                if (range.isValid(fileSize))
                  rangeFileOutput(filesInput, realResolvedPath, options.calculateETag(m), RangeValue(range.start, range.end, fileSize))
                    .map(Right(_))
                else (Left(StaticErrorOutput.RangeNotSatisfiable): Either[StaticErrorOutput, StaticOutput[FileRange]]).unit
              case None => wholeFileOutput(filesInput, realResolvedPath, options.calculateETag(m)).map(Right(_))
            }
        }
      }
    })
  }

  @tailrec
  private def resolveRealPath(realSystemPath: Path, path: List[String], default: Option[List[String]]): Either[StaticErrorOutput, Path] = {
    val resolved = path.foldLeft(realSystemPath)(_.resolve(_))

    if (!java.nio.file.Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
      default match {
        case Some(defaultPath) => resolveRealPath(realSystemPath, defaultPath, None)
        case None              => Left(StaticErrorOutput.NotFound)
      }
    } else {
      val realRequestedPath = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS)

      if (!realRequestedPath.startsWith(realSystemPath))
        Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, Path]
      else if (realRequestedPath.toFile.isDirectory) {
        resolveRealPath(realSystemPath, path :+ "index.html", default)
      } else {
        Right(realRequestedPath)
      }
    }
  }

  private def rangeFileOutput[F[_]](filesInput: StaticInput, file: Path, calculateETag: File => F[Option[ETag]], range: RangeValue)(implicit
      m: MonadError[F]
  ): F[StaticOutput[FileRange]] =
    fileOutput(
      filesInput,
      file,
      calculateETag,
      (lastModified, _, etag) =>
        StaticOutput.FoundPartial(
          FileRange(file.toFile, Some(range)),
          Some(Instant.ofEpochMilli(lastModified)),
          Some(range.contentLength),
          Some(contentTypeFromName(file.toFile.getName)),
          etag,
          Some(ContentRangeUnits.Bytes),
          Some(range.toContentRange.toString())
        )
    )

  private def wholeFileOutput[F[_]](filesInput: StaticInput, file: Path, calculateETag: File => F[Option[ETag]])(implicit
      m: MonadError[F]
  ): F[StaticOutput[FileRange]] = fileOutput(
    filesInput,
    file,
    calculateETag,
    (lastModified, fileLength, etag) =>
      StaticOutput.Found(
        FileRange(file.toFile),
        Some(Instant.ofEpochMilli(lastModified)),
        Some(fileLength),
        Some(contentTypeFromName(file.toFile.getName)),
        etag,
        None
      )
  )

  private def fileOutput[F[_]](
      filesInput: StaticInput,
      file: Path,
      calculateETag: File => F[Option[ETag]],
      result: (Long, Long, Option[ETag]) => StaticOutput[FileRange]
  )(implicit
      m: MonadError[F]
  ): F[StaticOutput[FileRange]] = for {
    etag <- calculateETag(file.toFile)
    lastModified <- m.blocking(file.toFile.lastModified())
    result <-
      if (isModified(filesInput, etag, lastModified))
        m.blocking(file.toFile.length())
          .map(fileLength => result(lastModified, fileLength, etag))
      else StaticOutput.NotModified.unit
  } yield result

}

/** @param fileFilter
  *   A file will be exposed only if this function returns `true`.
  * @param defaultFile
  *   path segments (relative to the system path from which files are read) of the file to return in case the one requested by the user
  *   isn't found. This is useful for SPA apps, where the same main application file needs to be returned regardless of the path.
  */
@deprecated("Use sttp.tapir.files.FilesOptions", since = "1.3.0")
case class FilesOptions[F[_]](
    calculateETag: MonadError[F] => File => F[Option[ETag]],
    fileFilter: List[String] => Boolean,
    defaultFile: Option[List[String]]
) {
  def calculateETag(f: File => F[Option[ETag]]): FilesOptions[F] = copy(calculateETag = _ => f)

  /** A file will be exposed only if this function returns `true`. */
  def fileFilter(f: List[String] => Boolean): FilesOptions[F] = copy(fileFilter = f)

  /** Path segments (relative to the system path from which files are read) of the file to return in case the one requested by the user
    * isn't found. This is useful for SPA apps, where the same main application file needs to be returned regardless of the path.
    */
  def defaultFile(d: List[String]): FilesOptions[F] = copy(defaultFile = Some(d))
}
object FilesOptions {
  def default[F[_]]: FilesOptions[F] = FilesOptions(Files.defaultEtag, _ => true, None)
}
