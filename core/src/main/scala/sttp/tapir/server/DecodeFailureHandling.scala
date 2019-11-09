package sttp.tapir.server

import sttp.tapir.{EndpointOutput, CodecFormat}

trait DecodeFailureHandling

object DecodeFailureHandling {
  case object NoMatch extends DecodeFailureHandling
  case class RespondWithResponse[T, CF <: CodecFormat, R](output: EndpointOutput[T], value: T) extends DecodeFailureHandling

  def noMatch: DecodeFailureHandling = NoMatch
  def response[T](output: EndpointOutput[T])(value: T): DecodeFailureHandling = RespondWithResponse(output, value)
}
