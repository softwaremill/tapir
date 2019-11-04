package tapir.server.play

import play.api.mvc.{AnyContent, RawBuffer, Request}
import tapir.server.{DecodeFailureHandler, LoggingOptions, ServerDefaults}

case class PlayServerOptions(decodeFailureHandler: DecodeFailureHandler[Request[RawBuffer]], loggingOptions: LoggingOptions)

object PlayServerOptions {
  implicit val default: PlayServerOptions = PlayServerOptions(ServerDefaults.decodeFailureHandler, LoggingOptions.default)
}
