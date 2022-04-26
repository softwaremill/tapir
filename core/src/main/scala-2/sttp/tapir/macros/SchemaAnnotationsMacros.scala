package sttp.tapir.macros

import sttp.tapir.SchemaAnnotations
import sttp.tapir.internal.SchemaAnnotationsMacro

trait SchemaAnnotationsMacros {
  implicit def derived[T]: SchemaAnnotations[T] = macro SchemaAnnotationsMacro.derived[T]
}
