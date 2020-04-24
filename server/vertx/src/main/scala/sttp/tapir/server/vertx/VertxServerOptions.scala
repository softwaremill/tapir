package sttp.tapir.server.vertx

import sttp.tapir.server.DecodeFailureHandler

case class VertxServerOptions(
  decodeFailureHandler: DecodeFailureHandler
)
