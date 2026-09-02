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

    // after the metadata annotations above, before the validators below
    val s2c = _customise(s2.asInstanceOf[Schema[Any]]).asInstanceOf[Schema[T]]

    val s3 = validate.foldLeft(s2c)((current, v) => current.validate(v))

    validateEach.foldLeft(s3)((current, v) => current.modifyUnsafe(Schema.ModifyCollectionElements)((_: Schema[Any]).validate(v)))
  }

  /** Transformation from the `@customise` annotations, composed in declaration order.
    *
    * Not a constructor parameter, to keep the generated `apply`, `unapply` and constructor signatures binary compatible (MiMa, 2.12/2.13).
    * Consequence: invisible to the generated `equals`, `hashCode`, `toString`, `unapply` and `productIterator`.
    */
  private var _customise: Schema[Any] => Schema[Any] = identity

  /** Composes `c` after the current transformation. */
  def withCustomise(c: Schema[Any] => Schema[Any]): SchemaAnnotations[T] = {
    val copy = this.copy[T]()
    copy._customise = _customise.andThen(c)
    copy
  }

  /** Replaces the generated `copy`, so that `_customise` is carried over.
    *
    * The signature must match the generated one exactly, type parameter included - otherwise Scala 3 generates its own `copy` as well, and
    * the two overloads make every defaulted call unresolvable. Hence the casts below, and hence `R` is not taken from the receiver: without
    * an expected type it is inferred as `Nothing`, so callers apply it explicitly (`copy[String](...)`).
    */
  def copy[R](
      description: Option[String] = this.description,
      encodedExample: Option[Any] = this.encodedExample,
      default: Option[(R, Option[Any])] = this.default.asInstanceOf[Option[(R, Option[Any])]],
      format: Option[String] = this.format,
      deprecated: Option[Boolean] = this.deprecated,
      hidden: Option[Boolean] = this.hidden,
      encodedName: Option[String] = this.encodedName,
      validate: List[Validator[R]] = this.validate.asInstanceOf[List[Validator[R]]],
      validateEach: List[Validator[Any]] = this.validateEach
  ): SchemaAnnotations[R] = {
    val c = new SchemaAnnotations[R](
      description,
      encodedExample,
      default,
      format,
      deprecated,
      hidden,
      encodedName,
      validate,
      validateEach
    )
    c._customise = this._customise
    c
  }
}

object SchemaAnnotations extends SchemaAnnotationsMacros {
  def empty[T]: SchemaAnnotations[T] = SchemaAnnotations(None, None, None, None, None, None, None, Nil, Nil)
}
