package sttp.tapir.server.interceptor

import sttp.tapir.EndpointOutput

case class ValuedEndpointOutput[T](output: EndpointOutput[T], value: T)
