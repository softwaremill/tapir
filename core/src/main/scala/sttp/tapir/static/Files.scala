package sttp.tapir.static

import sttp.model.headers.ETag
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.File
import java.nio.file.{LinkOption, Path, Paths}
import java.time.Instant
import scala.util.{Failure, Success, Try}

object Files {
  // inspired by org.http4s.server.staticcontent.FileService

  def apply[F[_]: MonadError](systemPath: String): StaticInput => F[Either[StaticErrorOutput, StaticOutput[File]]] =
    apply(systemPath, defaultEtag[F])

  def apply[F[_]: MonadError](
      systemPath: String,
      calculateETag: File => F[Option[ETag]]
  ): StaticInput => F[Either[StaticErrorOutput, StaticOutput[File]]] = {
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
  ): F[Either[StaticErrorOutput, StaticOutput[File]]] = {
    val resolved = filesInput.path.foldLeft(realSystemPath)(_.resolve(_))
    m.flatten(m.blocking {
      if (!java.nio.file.Files.exists(resolved, LinkOption.NOFOLLOW_LINKS))
        (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[File]]).unit
      else {
        val realRequestedPath = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS)
        if (!realRequestedPath.startsWith(realSystemPath))
          (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput[File]]).unit
        else fileOutput(filesInput, realRequestedPath, calculateETag).map(Right(_))
      }
    })
  }

  private def fileOutput[F[_]](filesInput: StaticInput, file: Path, calculateETag: File => F[Option[ETag]])(implicit
      m: MonadError[F]
  ): F[StaticOutput[File]] = for {
    etag <- calculateETag(file.toFile)
    lastModified <- m.blocking(file.toFile.lastModified())
    result <- {
      if (isModified(filesInput, etag, lastModified)) found(file, etag, lastModified)
      else StaticOutput.NotModified.unit
    }
  } yield result

  private def found[F[_]](file: Path, etag: Option[ETag], lastModified: Long)(implicit m: MonadError[F]): F[StaticOutput.Found[File]] = {
    m.blocking(file.toFile.length()).map { contentLength =>
      val contentType = contentTypeFromName(file.toFile.getName)
      StaticOutput.Found(file.toFile, Some(Instant.ofEpochMilli(lastModified)), Some(contentLength), Some(contentType), etag)
    }
  }
}
