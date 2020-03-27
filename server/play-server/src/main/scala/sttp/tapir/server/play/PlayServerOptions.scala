package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFileCreator}
import play.api.mvc._
import sttp.tapir.server.{DecodeFailureHandler, LoggingOptions, ServerDefaults}

import scala.concurrent.ExecutionContext

case class PlayServerOptions(
    decodeFailureHandler: DecodeFailureHandler[RequestHeader],
    loggingOptions: LoggingOptions,
    temporaryFileCreator: TemporaryFileCreator,
    defaultActionBuilder: ActionBuilder[Request, AnyContent],
    playBodyParsers: PlayBodyParsers
)

object PlayServerOptions {
  implicit def default(implicit mat: Materializer, ec: ExecutionContext): PlayServerOptions =
    PlayServerOptions(
      ServerDefaults.decodeFailureHandler,
      LoggingOptions.default,
      SingletonTemporaryFileCreator,
      DefaultActionBuilder.apply(PlayBodyParsers.apply().anyContent),
      PlayBodyParsers.apply()
    )
}
