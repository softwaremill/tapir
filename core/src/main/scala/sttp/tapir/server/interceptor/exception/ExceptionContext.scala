package sttp.tapir.server.interceptor.exception

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest

case class ExceptionContext(e: Throwable, endpoint: Endpoint[_, _, _, _], request: ServerRequest)
