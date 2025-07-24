package sttp.tapir.files

import sttp.model.ContentRangeUnits
import sttp.model.MediaType
import sttp.model.headers.ETag
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.FileRange
import sttp.tapir.RangeValue

import java.io.File
import java.net.URL
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.{Files => JFiles}
import java.time.Instant
import scala.annotation.tailrec

object Files {

  def head[F[_]](
      systemPath: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[Unit]]] = { implicit monad => filesInput =>
    get(systemPath, options)(monad)(filesInput)
      .map(_.map(_.withoutBody))
  }

  def get[F[_]](
      systemPath: String,
      options: FilesOptions[F] = FilesOptions.default[F]
  ): MonadError[F] => StaticInput => F[Either[StaticErrorOutput, StaticOutput[FileRange]]] = { implicit monad => filesInput =>
    MonadError[F]
      .blocking {
        // resolve the system path to a real path upfront, so that later security checks (that the user's request doesn't try to access
        // files outside of the system path) work properly
        val systemPathAsPath = Paths.get(systemPath)
        if (JFiles.exists(systemPathAsPath, LinkOption.NOFOLLOW_LINKS)) Some(systemPathAsPath.toRealPath()) else None
      }
      .flatMap {
        case Some(path) =>
          val resolveUrlFn: ResolveUrlFn = resolveSystemPathUrl(filesInput, options, path)
          files(filesInput, options, resolveUrlFn, fileRangeFromUrl)
        case None =>
          // if the system path doesn't exist, just return a 404
          MonadError[F].unit(Left(StaticErrorOutput.NotFound))
      }
  }

  def defaultEtag[F[_]]: MonadError[F] => Option[RangeValue] => URL => F[Option[ETag]] = monad => { range => url =>
    monad.blocking {
      val connection = url.openConnection()
      val lastModified = connection.getLastModified
      val length = connection.getContentLengthLong
      Some(defaultETag(lastModified, range, length))
    }
  }

  private def fileRangeFromUrl(
      url: URL,
      range: Option[RangeValue]
  ): FileRange = FileRange(
    new File(url.toURI),
    range
  )

  /** Creates a function of type ResolveUrlFn, which is capable of taking a relative path as a list of string segments, and finding the
    * actual full system path, considering additional parameters. For example, with a root system path of /home/user/files/ it can create a
    * function which takies List("dir1", "dir2", "file.txt") and tries to resolve /home/user/files/dir1/dir2/file.txt into a Url. The final
    * resolved file may also be resolved to a pre-gzipped sibling, an index.html file, or a default file given as a fallback, all depending
    * on additional parameters. See also Resources.resolveResourceUrl for an equivalent of this function but for resources under a
    * classloader.
    *
    * @param input
    *   request input parameters like path and headers, used together with options to apply filtering and look for possible pre-gzipped
    *   files if they are accepted
    * @param options
    *   additional options of the endpoint, defining filtering rules and pre-gzipped file support
    * @param systemPath
    *   the root system path where file resolution should happen
    * @return
    *   a function which can be used in general file resolution logic. This function takes path segments and an optional default fallback
    *   path segments and tries to resolve the file, then returns its full Url.
    */
  private def resolveSystemPathUrl[F[_]](input: StaticInput, options: FilesOptions[F], systemPath: Path): ResolveUrlFn = {

    @tailrec
    def resolveRec(path: List[String], default: Option[List[String]]): Either[StaticErrorOutput, ResolvedUrl] = {
      val resolved = path.foldLeft(systemPath)(_.resolve(_))
      val resolvedGzipped = resolveGzipSibling(resolved)
      if (useGzippedIfAvailable(input, options) && JFiles.exists(resolvedGzipped, LinkOption.NOFOLLOW_LINKS)) {
        val realRequestedPath = resolvedGzipped.toRealPath(LinkOption.NOFOLLOW_LINKS)
        if (!realRequestedPath.startsWith(resolvedGzipped))
          LeftUrlNotFound
        else
          Right(ResolvedUrl(realRequestedPath.toUri.toURL, contentTypeFromName(resolved.getFileName.toString), Some("gzip")))
      } else {
        if (!JFiles.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
          default match {
            case Some(defaultPath) => resolveRec(defaultPath, None)
            case None              => LeftUrlNotFound
          }
        } else {
          val realRequestedPath = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS)

          if (!realRequestedPath.startsWith(systemPath))
            LeftUrlNotFound
          else if (realRequestedPath.toFile.isDirectory) {
            resolveRec(path :+ "index.html", default)
          } else {
            Right(ResolvedUrl(realRequestedPath.toUri.toURL, contentTypeFromName(realRequestedPath.getFileName.toString), None))
          }
        }
      }
    }

    if (!options.fileFilter(input.path))
      (_, _) => LeftUrlNotFound
    else
      resolveRec _
  }

  private[files] def files[F[_], R](
      input: StaticInput,
      options: FilesOptions[F],
      resolveUrlFn: ResolveUrlFn,
      urlToResultFn: (URL, Option[RangeValue]) => R
  )(implicit
      m: MonadError[F]
  ): F[Either[StaticErrorOutput, StaticOutput[R]]] = {
    m.flatten(m.blocking {
      resolveUrlFn(input.path, options.defaultFile) match {
        case Left(error) =>
          (Left(error): Either[StaticErrorOutput, StaticOutput[R]]).unit
        case Right(ResolvedUrl(url, contentType, contentEncoding)) =>
          input.range match {
            case Some(range) =>
              val fileSize = url.openConnection().getContentLengthLong()
              if (range.isValid(fileSize)) {
                val rangeValue = RangeValue(range.start, range.end, fileSize)
                rangeFileOutput(input, url, options.calculateETag(m)(Some(rangeValue)), rangeValue, contentType, urlToResultFn)
                  .map(Right(_))
              } else (Left(StaticErrorOutput.RangeNotSatisfiable): Either[StaticErrorOutput, StaticOutput[R]]).unit
            case None =>
              wholeFileOutput(input, url, options.calculateETag(m)(None), contentType, contentEncoding, urlToResultFn).map(Right(_))
          }
      }
    })
  }

  private def resolveGzipSibling(path: Path): Path =
    path.resolveSibling(path.getFileName.toString + ".gz")

  private def rangeFileOutput[F[_], R](
      filesInput: StaticInput,
      url: URL,
      calculateETag: URL => F[Option[ETag]],
      range: RangeValue,
      contentType: MediaType,
      urlToResult: (URL, Option[RangeValue]) => R
  )(implicit
      m: MonadError[F]
  ): F[StaticOutput[R]] =
    fileOutput(
      filesInput,
      url,
      calculateETag,
      (lastModified, _, etag) =>
        StaticOutput.FoundPartial(
          urlToResult(url, Some(range)),
          Some(Instant.ofEpochMilli(lastModified)),
          Some(range.contentLength),
          Some(contentType),
          etag,
          Some(ContentRangeUnits.Bytes),
          Some(range.toContentRange.toString())
        )
    )

  private def wholeFileOutput[F[_], R](
      filesInput: StaticInput,
      url: URL,
      calculateETag: URL => F[Option[ETag]],
      contentType: MediaType,
      contentEncoding: Option[String],
      urlToResult: (URL, Option[RangeValue]) => R
  )(implicit
      m: MonadError[F]
  ): F[StaticOutput[R]] = fileOutput(
    filesInput,
    url,
    calculateETag,
    (lastModified, fileLength, etag) =>
      StaticOutput.Found(
        urlToResult(url, None),
        Some(Instant.ofEpochMilli(lastModified)),
        Some(fileLength),
        Some(contentType),
        etag,
        Some(ContentRangeUnits.Bytes),
        contentEncoding
      )
  )

  private def fileOutput[F[_], R](
      filesInput: StaticInput,
      url: URL,
      calculateETag: URL => F[Option[ETag]],
      result: (Long, Long, Option[ETag]) => StaticOutput[R]
  )(implicit
      m: MonadError[F]
  ): F[StaticOutput[R]] =
    for {
      etagOpt <- calculateETag(url)
      urlConnection <- m.blocking(url.openConnection())
      lastModified <- m.blocking(urlConnection.getLastModified())
      resourceResult <-
        if (isModified(filesInput, etagOpt, lastModified))
          m.blocking(urlConnection.getContentLengthLong).map(fileLength => result(lastModified, fileLength, etagOpt))
        else StaticOutput.NotModified.unit
    } yield resourceResult
}

/** @param fileFilter
  *   A file will be exposed only if this function returns `true`.
  * @param defaultFile
  *   path segments (relative to the system path from which files are read) of the file to return in case the one requested by the user
  *   isn't found. This is useful for SPA apps, where the same main application file needs to be returned regardless of the path.
  */
case class FilesOptions[F[_]](
    calculateETag: MonadError[F] => Option[RangeValue] => URL => F[Option[ETag]],
    fileFilter: List[String] => Boolean,
    useGzippedIfAvailable: Boolean = false,
    defaultFile: Option[List[String]]
) {
  def withUseGzippedIfAvailable: FilesOptions[F] = copy(useGzippedIfAvailable = true)

  def calculateETag(f: Option[RangeValue] => URL => F[Option[ETag]]): FilesOptions[F] = copy(calculateETag = _ => f)

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
