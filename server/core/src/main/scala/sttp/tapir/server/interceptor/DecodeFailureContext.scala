package sttp.tapir.server.interceptor

import sttp.tapir.model.ServerRequest
import sttp.tapir.{AnyEndpoint, DecodeResult, EndpointInput}

case class DecodeFailureContext(
    endpoint: AnyEndpoint,
    failingInput: EndpointInput[_],
    failure: DecodeResult.Failure,
    request: ServerRequest
)
