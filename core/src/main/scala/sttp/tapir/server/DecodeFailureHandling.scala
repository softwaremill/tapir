package sttp.tapir.server

import sttp.tapir.EndpointOutput

/** Describes the action to take, when decoding an input of a request fails. Should another endpoint be tried,
  * or should a response be sent.
  */
trait DecodeFailureHandling

object DecodeFailureHandling {
  case object NoMatch extends DecodeFailureHandling
  case class RespondWithResponse[T, R](output: EndpointOutput[T, R], value: T) extends DecodeFailureHandling

  def noMatch: DecodeFailureHandling = NoMatch
  def response[T, R](output: EndpointOutput[T, R])(value: T): DecodeFailureHandling = RespondWithResponse(output, value)
}
