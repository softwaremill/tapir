package sttp.tapir.server.model

import sttp.tapir.EndpointOutput

case class ValuedEndpointOutput[T](output: EndpointOutput[T], value: T) {
  def prepend[U](otherOutput: EndpointOutput[U], otherValue: U): ValuedEndpointOutput[(U, T)] =
    ValuedEndpointOutput(otherOutput.and(output), (otherValue, value))

  def append[U](otherOutput: EndpointOutput[U], otherValue: U): ValuedEndpointOutput[(T, U)] =
    ValuedEndpointOutput(output.and(otherOutput), (value, otherValue))
}
