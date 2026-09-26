package sttp.tapir.internal

import sttp.tapir.{SchemaAnnotations, Validator}

/** Scala 3 generates the full `copy` alongside, so only the 9-parameter overload (kept for binary compatibility) is defined here. */
trait SchemaAnnotationsCompat[T] { this: SchemaAnnotations[T] =>
  def copy(
      description: Option[String],
      encodedExample: Option[Any],
      default: Option[(T, Option[Any])],
      format: Option[String],
      deprecated: Option[Boolean],
      hidden: Option[Boolean],
      encodedName: Option[String],
      validate: List[Validator[T]],
      validateEach: List[Validator[Any]]
  ): SchemaAnnotations[T] =
    SchemaAnnotations(description, encodedExample, default, format, deprecated, hidden, encodedName, validate, validateEach, customise)
}
