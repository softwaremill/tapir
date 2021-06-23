package sttp.tapir

import sttp.tapir.generic.internal.EndpointInputAnnotationsMacro
import sttp.tapir.generic.internal.EndpointOutputAnnotationsMacro

package object annotations {
  def deriveEndpointInput[A]: EndpointInput[A] =
    macro EndpointInputAnnotationsMacro.generateEndpointInput[A]

  def deriveEndpointOutput[A]: EndpointOutput[A] =
    macro EndpointOutputAnnotationsMacro.generateEndpointOutput[A]
}
