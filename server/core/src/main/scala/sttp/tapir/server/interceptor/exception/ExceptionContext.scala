package sttp.tapir.server.interceptor.exception

import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest

case class ExceptionContext(e: Throwable, endpoint: AnyEndpoint, request: ServerRequest)
