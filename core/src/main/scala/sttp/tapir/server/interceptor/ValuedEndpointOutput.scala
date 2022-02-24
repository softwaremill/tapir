package sttp.tapir.server.interceptor

import sttp.tapir.{emptyOutput, EndpointOutput}

case class ValuedEndpointOutput[T](output: EndpointOutput[T], value: T) {
  def prepend[U](otherOutput: EndpointOutput[U], otherValue: U): ValuedEndpointOutput[(U, T)] =
    ValuedEndpointOutput(otherOutput.and(output), (otherValue, value))

  def append[U](otherOutput: EndpointOutput[U], otherValue: U): ValuedEndpointOutput[(T, U)] =
    ValuedEndpointOutput(output.and(otherOutput), (value, otherValue))
}

object ValuedEndpointOutput {
  val Empty: ValuedEndpointOutput[Unit] = ValuedEndpointOutput(emptyOutput, ())
}
