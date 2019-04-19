package tapir.server.http4s

import java.io.File

import cats.effect.{ContextShift, Sync}
import org.http4s.Request
import tapir.Defaults
import tapir.server.{DecodeFailureHandler, ServerDefaults}

import scala.concurrent.ExecutionContext

case class Http4sServerOptions[F[_]](
    createFile: (ExecutionContext, Request[F]) => F[File], // TODO: include request/part headers, information if this is a part?
    blockingExecutionContext: ExecutionContext,
    ioChunkSize: Int,
    decodeFailureHandler: DecodeFailureHandler[Request[F]]
)

object Http4sServerOptions {
  def defaultCreateFile[F[_]](implicit sync: Sync[F], cs: ContextShift[F]): (ExecutionContext, Request[F]) => F[File] =
    (ec, _) => cs.evalOn(ec)(sync.delay(Defaults.createTempFile()))

  implicit def default[F[_]: Sync: ContextShift]: Http4sServerOptions[F] =
    Http4sServerOptions(defaultCreateFile, ExecutionContext.Implicits.global, 8192, ServerDefaults.decodeFailureHandler)
}
