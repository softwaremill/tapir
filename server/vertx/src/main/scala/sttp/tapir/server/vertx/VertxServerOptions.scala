package sttp.tapir.server.vertx

import sttp.tapir.server.DecodeFailureHandler

import scala.concurrent.ExecutionContext

case class VertxServerOptions(
  decodeFailureHandler: DecodeFailureHandler,
  private val specificExecutionContext: Option[ExecutionContext]
) {
  def executionContextOr(default: ExecutionContext): ExecutionContext =
    specificExecutionContext.getOrElse(default)
}
