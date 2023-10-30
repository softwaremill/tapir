package sttp.tapir.server.interceptor.reject

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.RequestResult

case class RejectContext(failure: RequestResult.Failure, request: ServerRequest)
