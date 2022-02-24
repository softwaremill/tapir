package sttp.tapir.internal

import sttp.tapir.Schema.SName
import sttp.tapir.{Schema, Validator}

final case class SchemaAnnotations[T](
    description: Option[String],
    encodedExample: Option[Any],
    default: Option[T],
    format: Option[String],
    deprecated: Option[Boolean],
    encodedName: Option[String],
    validate: Option[Validator[T]]
) {
  private case class SchemaEnrich(current: Schema[T]) {
    def optionally(f: Schema[T] => Option[Schema[T]]): SchemaEnrich = f(current).map(SchemaEnrich.apply).getOrElse(this)
  }

  def enrich(s: Schema[T]): Schema[T] = {
    SchemaEnrich(s)
      .optionally(s => description.map(s.description(_)))
      .optionally(s => encodedExample.map(s.encodedExample(_)))
      .optionally(s => default.map(s.default(_)))
      .optionally(s => format.map(s.format(_)))
      .optionally(s => deprecated.map(s.deprecated(_)))
      .optionally(s => encodedName.map(en => s.name(SName(en))))
      .optionally(s => validate.map(s.validate))
      .current
  }
}

object SchemaAnnotations extends SchemaAnnotationsMacro
