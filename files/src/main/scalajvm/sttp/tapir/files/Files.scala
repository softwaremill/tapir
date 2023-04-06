package sttp.tapir.files

import sttp.model.ContentRangeUnits
import sttp.model.headers.ETag
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{FileRange, RangeValue}

import java.io.File
import java.net.URL
import java.nio.file.{FileSystems, LinkOption, Path, Paths}
import java.time.Instant
import scala.annotation.tailrec

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
            resolveRealPath(Paths.get(systemPath).toRealPath(), input.path, options.defaultFile, options.useGzippedIfAvailable) match {
              case Left(error) => Left(error)
              case Right((resolved, _)) =>
                val file = resolved.toFile
                Right(
                  HeadOutput.Found(
                    Some(ContentRangeUnits.Bytes),
                    Some(file.length()),
                    Some(contentTypeFromName(file.getName))
                  )
                )
            }
          }
        }
    }

  def head2[F[_]](
      systemPath: Path,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => HeadInput => F[Either[StaticErrorOutput, HeadOutput]] = implicit monad =>
    input => {
      MonadError[F]
        .blocking {
          if (!options.fileFilter(input.path)) {
            Left(StaticErrorOutput.NotFound)
          } else {
            resolveRealPath(systemPath.toRealPath(), input.path, options.defaultFile, options.useGzippedIfAvailable) match {
              case Left(error) => Left(error)
              case Right((resolved, _)) =>
                val file = resolved.toFile
                Right(
                  HeadOutput.Found(
                    Some(ContentRangeUnits.Bytes),
                    Some(file.length()),
                    Some(contentTypeFromName(file.getName))
                  )
                )
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

  def get2[F[_]](
      systemPath: Path,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[FileRange]]] = { implicit monad => filesInput =>
    MonadError[F].blocking(systemPath).flatMap(path => files(path, options)(filesInput))
  }

  def getRes2[F[_]](
      res: URL,
      systemPath: Path,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[FileRange]]] = { implicit monad => filesInput =>
    if (res.getProtocol == "jar") {
      val fs = FileSystems.newFileSystem(new File(res.getPath).toPath)
      fs.getPath(res.getPath)
    }
    MonadError[F].blocking(systemPath.toRealPath()).flatMap(path => files(path, options)(filesInput))
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
      val useGzippedIfAvailable =
        filesInput.range.isEmpty && options.useGzippedIfAvailable && filesInput.acceptEncoding.exists(_.equals("gzip"))
      if (!options.fileFilter(filesInput.path))
        (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[FileRange]]).unit
      else {
        resolveRealPath(realSystemPath, filesInput.path, options.defaultFile, useGzippedIfAvailable) match {
          case Left(error) =>
            println(s">>>>>>>>>>> err")

            (Left(error): Either[StaticErrorOutput, StaticOutput[FileRange]]).unit
          case Right((realResolvedPath, contentEncoding)) =>
            println(s">>>>>>>>>>> ${realResolvedPath}")
            filesInput.range match {
              case Some(range) =>
                val fileSize = realResolvedPath.toFile.length()
                if (range.isValid(fileSize))
                  rangeFileOutput(filesInput, realResolvedPath, options.calculateETag(m), RangeValue(range.start, range.end, fileSize))
                    .map(Right(_))
                else (Left(StaticErrorOutput.RangeNotSatisfiable): Either[StaticErrorOutput, StaticOutput[FileRange]]).unit
              case None => wholeFileOutput(filesInput, realResolvedPath, options.calculateETag(m), contentEncoding).map(Right(_))
            }
        }
      }
    })
  }
  @tailrec
  private def resolveRealPath(
      realSystemPath: Path,
      path: List[String],
      default: Option[List[String]],
      useGzippedIfAvailable: Boolean
  ): Either[StaticErrorOutput, (Path, Option[String])] = {
    val resolved = path.foldLeft(realSystemPath)(_.resolve(_))
    val resolvedGzipped = resolved.resolveSibling(realSystemPath.getFileName.toString + ".gz")

    if (useGzippedIfAvailable && java.nio.file.Files.exists(resolvedGzipped, LinkOption.NOFOLLOW_LINKS)) {
      val realRequestedPath = resolvedGzipped.toRealPath(LinkOption.NOFOLLOW_LINKS)
      if (!realRequestedPath.startsWith(realSystemPath.resolveSibling(realSystemPath.getFileName.toString + ".gz")))
        Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, (Path, Option[String])]
      else
        Right((realRequestedPath, Some("gzip")))
    } else {
      if (!java.nio.file.Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
        default match {
          case Some(defaultPath) => resolveRealPath(realSystemPath, defaultPath, None, useGzippedIfAvailable)
          case None              => Left(StaticErrorOutput.NotFound)
        }
      } else {
        val realRequestedPath = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS)

        if (!realRequestedPath.startsWith(realSystemPath))
          Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, (Path, Option[String])]
        else if (realRequestedPath.toFile.isDirectory) {
          resolveRealPath(realSystemPath, path :+ "index.html", default, useGzippedIfAvailable)
        } else {
          Right((realRequestedPath, None))
        }
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

  private def wholeFileOutput[F[_]](
      filesInput: StaticInput,
      file: Path,
      calculateETag: File => F[Option[ETag]],
      contentEncoding: Option[String]
  )(implicit
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
        contentEncoding
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
case class FilesOptions[F[_]](
    calculateETag: MonadError[F] => File => F[Option[ETag]],
    fileFilter: List[String] => Boolean,
    useGzippedIfAvailable: Boolean = false,
    defaultFile: Option[List[String]]
) {
  def withUseGzippedIfAvailable: FilesOptions[F] = copy(useGzippedIfAvailable = true)

  def calculateETag(f: File => F[Option[ETag]]): FilesOptions[F] = copy(calculateETag = _ => f)

  /** A file will be exposed only if this function returns `true`. */
  def fileFilter(f: List[String] => Boolean): FilesOptions[F] = copy(fileFilter = f)

  /** Path segments (relative to the system path from which files are read) of the file to return in case the one requested by the user
    * isn't found. This is useful for SPA apps, where the same main application file needs to be returned regardless of the path.
    */
  def defaultFile(d: List[String]): FilesOptions[F] = copy(defaultFile = Some(d))
}
object FilesOptions {
  def default[F[_]]: FilesOptions[F] = FilesOptions(Files.defaultEtag, _ => true, useGzippedIfAvailable = false, None)
}
