package sttp.tapir.server

import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

case class DecodeFailureContext(input: EndpointInput[_, _], failure: DecodeResult.Failure, endpoint: Endpoint[_, _, _, _])
