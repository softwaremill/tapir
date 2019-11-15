package sttp.tapir.server

import sttp.tapir.{DecodeFailure, EndpointInput}

case class DecodeFailureContext[+R](request: R, input: EndpointInput.Single[_], failure: DecodeFailure)
