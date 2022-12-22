package sttp.tapir.server.interceptor.log

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest

case class ExceptionContext[A, U](
    endpoint: Endpoint[A, _, _, _, _],
    securityInput: Option[A],
    principal: Option[U],
    request: ServerRequest
)
