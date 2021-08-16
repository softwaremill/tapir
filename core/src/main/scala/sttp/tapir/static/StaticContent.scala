package sttp.tapir.static

import sttp.model.MediaType
import sttp.model.headers.ETag
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.File
import java.net.URLConnection
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.time.Instant
import scala.util.{Failure, Success, Try}

object StaticContent {
  // inspired by org.http4s.server.staticcontent.FileService

  def files[F[_]: MonadError](systemPath: String): StaticInput => F[Either[StaticErrorOutput, StaticOutput]] =
    files(systemPath, defaultCalculateEtag[F])

  def files[F[_]: MonadError](
      systemPath: String,
      calculateETag: File => F[Option[ETag]]
  ): StaticInput => F[Either[StaticErrorOutput, StaticOutput]] = {
    Try(Paths.get(systemPath).toRealPath()) match {
      case Success(realSystemPath) => (filesInput: StaticInput) => files(realSystemPath, calculateETag)(filesInput)
      case Failure(e)              => _ => MonadError[F].error(e)
    }
  }

  def defaultCalculateEtag[F[_]: MonadError](file: File): F[Option[ETag]] = MonadError[F].blocking {
    if (file.isFile) Some(ETag(s"${file.lastModified().toHexString}-${file.length().toHexString}"))
    else None
  }

  private def files[F[_]](realSystemPath: Path, calculateETag: File => F[Option[ETag]])(filesInput: StaticInput)(implicit
      m: MonadError[F]
  ): F[Either[StaticErrorOutput, StaticOutput]] = {
    val resolved = filesInput.path.foldLeft(realSystemPath)(_.resolve(_))
    m.flatten(m.blocking {
      if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS))
        (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput]).unit
      else {
        val realRequestedPath = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS)
        if (!realRequestedPath.startsWith(realSystemPath)) (Left(StaticErrorOutput.NotFound): Either[StaticErrorOutput, StaticOutput]).unit
        else fileOutput(filesInput, realRequestedPath, calculateETag).map(Right(_))
      }
    })
  }

  private def fileOutput[F[_]](filesInput: StaticInput, file: Path, calculateETag: File => F[Option[ETag]])(implicit
      m: MonadError[F]
  ): F[StaticOutput] = for {
    etag <- calculateETag(file.toFile)
    lastModified <- m.blocking(file.toFile.lastModified())
    result <- {
      if (isModified(filesInput, etag, lastModified)) found(file, etag, lastModified)
      else StaticOutput.NotModified.unit
    }
  } yield result

  private def found[F[_]](file: Path, etag: Option[ETag], lastModified: Long)(implicit m: MonadError[F]): F[StaticOutput.Found] = {
    m.blocking(file.toFile.length()).map { contentLength =>
      val contentType =
        Option(URLConnection.guessContentTypeFromName(file.toFile.getName))
          .flatMap(ct => MediaType.parse(ct).toOption)
          .getOrElse(MediaType.ApplicationOctetStream)
      StaticOutput.Found(file.toFile, Some(Instant.ofEpochMilli(lastModified)), Some(contentLength), Some(contentType), etag)
    }
  }

  private def isModified(filesInput: StaticInput, etag: Option[ETag], lastModified: Long): Boolean = {
    etag match {
      case None => isModifiedByModifiedSince(filesInput, lastModified)
      case Some(et) =>
        val ifNoneMatch = filesInput.ifNoneMatch.getOrElse(Nil)
        if (ifNoneMatch.nonEmpty) ifNoneMatch.forall(e => e.tag != et.tag)
        else true
    }
  }

  private def isModifiedByModifiedSince(filesInput: StaticInput, lastModified: Long): Boolean = filesInput.ifModifiedSince match {
    case Some(i) => lastModified > i.toEpochMilli
    case None    => true
  }

  //def resources(classLoader, prefix)

}
