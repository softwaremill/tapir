package sttp.tapir.server.interceptor.log

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest

case class ExceptionContext[A, U](
    endpoint: Endpoint[A, ?, ?, ?, ?],
    securityInput: Option[A],
    principal: Option[U],
    request: ServerRequest
)
