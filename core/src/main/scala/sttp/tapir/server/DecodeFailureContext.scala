package sttp.tapir.server

import sttp.tapir.{DecodeResult, EndpointInput}

case class DecodeFailureContext(input: EndpointInput[_], failure: DecodeResult.Failure)
