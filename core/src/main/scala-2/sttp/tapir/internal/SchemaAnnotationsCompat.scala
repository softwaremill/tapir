package sttp.tapir.internal

import sttp.tapir.{Schema, SchemaAnnotations, Validator}

/** Scala 2 doesn't generate `copy` for a case class which has any member named `copy`, so the 9-parameter overload (kept for binary
  * compatibility) requires writing the full one by hand. Unlike the generated one, it doesn't allow changing `T`.
  */
trait SchemaAnnotationsCompat[T] { this: SchemaAnnotations[T] =>
  def copy(
      description: Option[String] = this.description,
      encodedExample: Option[Any] = this.encodedExample,
      default: Option[(T, Option[Any])] = this.default,
      format: Option[String] = this.format,
      deprecated: Option[Boolean] = this.deprecated,
      hidden: Option[Boolean] = this.hidden,
      encodedName: Option[String] = this.encodedName,
      validate: List[Validator[T]] = this.validate,
      validateEach: List[Validator[Any]] = this.validateEach,
      customise: List[Schema[T] => Schema[T]] = this.customise
  ): SchemaAnnotations[T] =
    SchemaAnnotations(description, encodedExample, default, format, deprecated, hidden, encodedName, validate, validateEach, customise)

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
