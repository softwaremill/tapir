package tapir.server

import tapir.{EndpointOutput, MediaType}

trait DecodeFailureHandling

object DecodeFailureHandling {
  case object NoMatch extends DecodeFailureHandling
  case class RespondWithResponse[T, M <: MediaType, R](output: EndpointOutput[T], value: T) extends DecodeFailureHandling

  def noMatch: DecodeFailureHandling = NoMatch
  def response[T](output: EndpointOutput[T])(value: T): DecodeFailureHandling = RespondWithResponse(output, value)
}
