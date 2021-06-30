package sttp.tapir.macros

import sttp.tapir.EndpointInput
import sttp.tapir.generic.internal.EndpointInputAnnotationsMacro

trait EndpointInputMacros {
  def derived[A]: EndpointInput[A] = macro EndpointInputAnnotationsMacro.generateEndpointInput[A]
}
