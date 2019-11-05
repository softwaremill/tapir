package tapir.server.play

import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFileCreator}
import play.api.mvc.{RawBuffer, Request, RequestHeader}
import tapir.server.{DecodeFailureHandler, LoggingOptions, ServerDefaults}

case class PlayServerOptions(
    decodeFailureHandler: DecodeFailureHandler[RequestHeader],
    loggingOptions: LoggingOptions,
    temporaryFileCreator: TemporaryFileCreator
)

object PlayServerOptions {
  implicit val default: PlayServerOptions =
    PlayServerOptions(ServerDefaults.decodeFailureHandler, LoggingOptions.default, SingletonTemporaryFileCreator)
}
