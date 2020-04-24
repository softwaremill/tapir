package sttp.tapir.server.vertx

import sttp.tapir.server.{DecodeFailureHandler, DefaultDecodeFailureHandler}

case class VertxServerOptions(
  decodeFailureHandler: DecodeFailureHandler
)
