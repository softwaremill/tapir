package sttp.tapir

import sttp.tapir.generic.internal.EndpointInputAnnotations

package object annotations {
  def deriveEndpointInput[A]: EndpointInput[A] =
    macro EndpointInputAnnotations.deriveEndpointInput[A]
}
