package sttp.tapir.static

import sttp.model.ContentRangeUnits
import sttp.monad.MonadError

import java.nio.file.Paths
import scala.util.{Failure, Success, Try}

object FilesUtil {

  def apply[F[_]: MonadError](
      systemPath: String
  ): HeadInput => F[Either[StaticErrorOutput, HeadOutput]] = {
    Try(Paths.get(systemPath).toRealPath()) match {
      case Success(realSystemPath) =>
        _ =>
          val file = realSystemPath.toFile
          MonadError[F].blocking(
            Right(
              HeadOutput.SupportRanges(
                Some(ContentRangeUnits.Bytes),
                Some(file.length()),
                Some(contentTypeFromName(file.getName))
              )
            )
          )
      case Failure(e) => _ => MonadError[F].error(e)
    }
  }

}
