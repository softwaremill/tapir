package sttp.tapir.server

import sttp.tapir.{DecodeFailure, EndpointInput}

case class DecodeFailureContext(input: EndpointInput.Single[_], failure: DecodeFailure)
