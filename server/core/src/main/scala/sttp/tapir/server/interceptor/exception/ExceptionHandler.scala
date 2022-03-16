package sttp.tapir.server.interceptor.exception

import sttp.model.StatusCode
import sttp.tapir.{server, _}
import sttp.tapir.server.model.ValuedEndpointOutput

trait ExceptionHandler {
  def apply(ctx: ExceptionContext): Option[ValuedEndpointOutput[_]]
}

case class DefaultExceptionHandler(response: (StatusCode, String) => ValuedEndpointOutput[_]) extends ExceptionHandler {
  override def apply(ctx: ExceptionContext): Option[ValuedEndpointOutput[_]] =
    Some(response(StatusCode.InternalServerError, "Internal server error"))
}

object DefaultExceptionHandler {
  val handler: DefaultExceptionHandler =
    DefaultExceptionHandler((sc, m) => server.model.ValuedEndpointOutput(statusCode.and(stringBody), (sc, m)))
}
