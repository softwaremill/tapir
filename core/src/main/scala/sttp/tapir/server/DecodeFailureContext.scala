package sttp.tapir.server

import sttp.tapir.{DecodeFailure, EndpointInput}

case class DecodeFailureContext(input: EndpointInput[_], failure: DecodeFailure)
