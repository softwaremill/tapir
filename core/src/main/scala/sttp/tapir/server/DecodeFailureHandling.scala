package sttp.tapir.server

import sttp.tapir.{EndpointOutput, CodecFormat}

/**
  * Describes the action to take, when decoding an input of a request fails. Should another endpoint be tried,
  * or should a response be sent.
  */
trait DecodeFailureHandling

object DecodeFailureHandling {
  case object NoMatch extends DecodeFailureHandling
  case class RespondWithResponse[T, CF <: CodecFormat, R](output: EndpointOutput[T], value: T) extends DecodeFailureHandling

  def noMatch: DecodeFailureHandling = NoMatch
  def response[T](output: EndpointOutput[T])(value: T): DecodeFailureHandling = RespondWithResponse(output, value)
}
