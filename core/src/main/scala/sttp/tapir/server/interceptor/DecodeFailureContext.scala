package sttp.tapir.server.interceptor

import sttp.tapir.model.ServerRequest
import sttp.tapir.{AnyEndpoint, DecodeResult, EndpointInput}

case class DecodeFailureContext(
    failingInput: EndpointInput[_],
    failure: DecodeResult.Failure,
    endpoint: AnyEndpoint,
    request: ServerRequest
)
