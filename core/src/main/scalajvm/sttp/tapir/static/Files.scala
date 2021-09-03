package sttp.tapir.static

import sttp.model.headers.ETag
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.RangeValue
import sttp.tapir.internal.TapirFile

import java.io.File
import java.nio.file.{LinkOption, Path, Paths}
import java.time.Instant
import scala.util.{Failure, Success, Try}

object Files {
  // inspired by org.http4s.server.staticcontent.FileService

  def apply[F[_]: MonadError](systemPath: String): StaticInput => F[Either[StaticErrorOutput, StaticOutput[TapirFile]]] =
    apply(systemPath, defaultEtag[F])

  def apply[F[_]: MonadError](
      systemPath: String,
      calculateETag: File => F[Option[ETag]]
  ): StaticInput => F[Either[StaticErrorOutput, StaticOutput[TapirFile]]] = {
    Try(Paths.get(systemPath).toRealPath()) match {
      case Success(realSystemPath) => (filesInput: StaticInput) => files(realSystemPath, calculateETag)(filesInput)
      case Failure(e)              => _ => MonadError[F].error(e)
    }
  }

  def defaultEtag[F[_]: MonadError](file: File): F[Option[ETag]] = MonadError[F].blocking {
    if (file.isFile) Some(defaultETag(file.lastModified(), file.length()))
    else None
  }

  private def files[F[_]](realSystemPath: Path, calculateETag: File => F[Option[ETag]])(filesInput: StaticInput)(implicit
      m: MonadError[F]
  ): F[Either[StaticErrorOutput, StaticOutput[TapirFile]]] = {
    val resolved = filesInput.path.foldLeft(realSystemPath)(_.resolve(_))
    m.flatten(m.blocking {
      if (!java.nio.file.Files.exists(resolved, LinkOption.NOFOLLOW_LINKS))
        (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[TapirFile]]).unit
      else {
        val realRequestedPath = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS)
        if (!realRequestedPath.startsWith(realSystemPath))
          (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[TapirFile]]).unit
        else if (realRequestedPath.toFile.isDirectory) {
          files(realSystemPath, calculateETag)(filesInput.copy(path = filesInput.path :+ "index.html"))
        } else {
          filesInput.range match {
            case Some(value) =>
              if (value.validate(realRequestedPath.toFile.length())) fileOutput(filesInput, realRequestedPath, calculateETag, value).map(Right(_))
              else (Left(StaticErrorOutput.RangeNotSatisfiable): Either[StaticErrorOutput, StaticOutput[TapirFile]]).unit
            case None => fileOutput(filesInput, realRequestedPath, calculateETag).map(Right(_))
          }
        }
      }
    })
  }

  private def fileOutput[F[_]](filesInput: StaticInput, file: Path, calculateETag: File => F[Option[ETag]], range: RangeValue)(implicit
    m: MonadError[F]
  ): F[StaticOutput[TapirFile]] = for {
    etag <- calculateETag(file.toFile)
    lastModified <- m.blocking(file.toFile.lastModified())
    result <-
      if (isModified(filesInput, etag, lastModified))
        m.blocking(file.toFile.length()).map(contentLength =>
          StaticOutput.Found(TapirFile.fromFile(file.toFile, range), Some(Instant.ofEpochMilli(lastModified)), Some(contentLength), Some(contentTypeFromName(file.toFile.getName)), etag, Some("bytes"), Some(range.toContentRange(contentLength))))
      else StaticOutput.NotModified.unit
  } yield result

  private def fileOutput[F[_]](filesInput: StaticInput, file: Path, calculateETag: File => F[Option[ETag]])(implicit
      m: MonadError[F]
  ): F[StaticOutput[TapirFile]] = for {
    etag <- calculateETag(file.toFile)
    lastModified <- m.blocking(file.toFile.lastModified())
    result <-
      if (isModified(filesInput, etag, lastModified))
        m.blocking(file.toFile.length()).map(contentLength =>
          StaticOutput.Found(TapirFile.fromFile(file.toFile), Some(Instant.ofEpochMilli(lastModified)), Some(contentLength), Some(contentTypeFromName(file.toFile.getName)), etag))
      else StaticOutput.NotModified.unit
  } yield result


}
