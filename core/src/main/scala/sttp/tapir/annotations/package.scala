package sttp.tapir

import sttp.tapir.generic.internal.EndpointInputAnnotations
import sttp.tapir.generic.internal.EndpointOutputAnnotations

package object annotations {
  def deriveEndpointInput[A]: EndpointInput[A, Any] =
    macro EndpointInputAnnotations.deriveEndpointInput[A]

  def deriveEndpointOutput[A]: EndpointOutput[A, Any] =
    macro EndpointOutputAnnotations.deriveEndpointOutput[A]
}
