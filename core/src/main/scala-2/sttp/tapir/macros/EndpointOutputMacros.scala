package sttp.tapir.macros

import sttp.tapir.EndpointOutput
import sttp.tapir.generic.internal.EndpointOutputAnnotationsMacro

trait EndpointOutputMacros {
  def derived[A]: EndpointOutput[A] = macro EndpointOutputAnnotationsMacro.generateEndpointOutput[A]
}
