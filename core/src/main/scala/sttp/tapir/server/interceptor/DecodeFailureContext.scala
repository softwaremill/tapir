package sttp.tapir.server.interceptor

import sttp.tapir.model.ServerRequest
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

case class DecodeFailureContext(
    failingInput: EndpointInput[_],
    failure: DecodeResult.Failure,
    endpoint: Endpoint[_, _, _, _],
    request: ServerRequest
)
