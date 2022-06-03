package sttp.tapir.macros

import sttp.tapir.SchemaAnnotations
import sttp.tapir.internal.SchemaAnnotationsMacro

trait SchemaAnnotationsMacros {
  implicit inline def derived[T]: SchemaAnnotations[T] = ${ SchemaAnnotationsMacro.derived[T] }
}
