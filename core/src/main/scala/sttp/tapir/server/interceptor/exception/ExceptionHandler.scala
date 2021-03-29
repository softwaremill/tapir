package sttp.tapir.server.interceptor.exception

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.interceptor.ValuedEndpointOutput

trait ExceptionHandler {
  def apply(ctx: ExceptionContext): Option[ValuedEndpointOutput[_]]
}

object DefaultExceptionHandler extends ExceptionHandler {
  override def apply(ctx: ExceptionContext): Option[ValuedEndpointOutput[_]] =
    Some(ValuedEndpointOutput(statusCode.and(stringBody), (StatusCode.InternalServerError, "Internal server error")))
}
