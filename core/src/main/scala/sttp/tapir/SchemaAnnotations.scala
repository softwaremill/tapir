package sttp.tapir

import sttp.tapir.Schema.SName
import sttp.tapir.macros.SchemaAnnotationsMacros

import scala.Function.tupled

final case class SchemaAnnotations[T](
    description: Option[String],
    encodedExample: Option[Any],
    default: Option[(T, Option[Any])],
    format: Option[String],
    deprecated: Option[Boolean],
    hidden: Option[Boolean],
    encodedName: Option[String],
    validate: List[Validator[T]],
    validateEach: List[Validator[Any]]
) {

  private case class SchemaEnrich(current: Schema[T]) {
    def optionally(f: Schema[T] => Option[Schema[T]]): SchemaEnrich = f(current).map(SchemaEnrich.apply).getOrElse(this)
  }

  def enrich(s: Schema[T]): Schema[T] = {
    val s2 = SchemaEnrich(s)
      .optionally(s => description.map(s.description(_)))
      .optionally(s => encodedExample.map(s.encodedExample(_)))
      .optionally(s => default.map(tupled(s.default(_, _))))
      .optionally(s => format.map(s.format(_)))
      .optionally(s => deprecated.map(s.deprecated(_)))
      .optionally(s => hidden.map(s.hidden(_)))
      .optionally(s => encodedName.map(en => s.name(SName(en))))
      .current

    val s3 = validate.foldLeft(s2)((current, v) => current.validate(v))

    validateEach.foldLeft(s3)((current, v) => current.modifyUnsafe(Schema.ModifyCollectionElements)((_: Schema[Any]).validate(v)))
  }
}

object SchemaAnnotations extends SchemaAnnotationsMacros {
  def empty[T]: SchemaAnnotations[T] = SchemaAnnotations(None, None, None, None, None, None, None, Nil, Nil)
}
