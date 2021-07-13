package sttp.tapir.server.interceptor

import sttp.model.StatusCode
import sttp.tapir.model.ServerRequest
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

case class DecodeFailureContext(
    failingInput: EndpointInput[_],
    failure: DecodeResult.Failure,
    endpoint: Endpoint[_, _, _, _],
    request: ServerRequest
)

object DecodeFailureContext {
  def listToStatusCode(decodeFailureContexts: List[DecodeFailureContext]): StatusCode = {
    val methodMismatch = decodeFailureContexts.map(_.failingInput).exists {
      case _: EndpointInput.FixedMethod[_] => true
      case _                               => false
    }
    if (methodMismatch) StatusCode.MethodNotAllowed else StatusCode.NotFound
  }
}
