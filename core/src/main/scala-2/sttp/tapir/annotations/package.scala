package sttp.tapir

import sttp.tapir.generic.internal.EndpointInputAnnotations
import sttp.tapir.generic.internal.EndpointOutputAnnotations

package object annotations {
  def deriveEndpointInput[A]: EndpointInput[A] =
    macro EndpointInputAnnotations.deriveEndpointInput[A]

  def deriveEndpointOutput[A]: EndpointOutput[A] =
    macro EndpointOutputAnnotations.deriveEndpointOutput[A]
}
