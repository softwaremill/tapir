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
      .optionally(s => _customise.map(c => c(s.asInstanceOf[Schema[Any]]).asInstanceOf[Schema[T]]))
      .current

    val s3 = validate.foldLeft(s2)((current, v) => current.validate(v))

    validateEach.foldLeft(s3)((current, v) => current.modifyUnsafe(Schema.ModifyCollectionElements)((_: Schema[Any]).validate(v)))
  }

  /** Customise transformation function taken from @cusomise annotation Extraction was accidentally omitted when the annotation was
    * introduced
    *
    * Stored as a private var only to keep backward compatibility. Moving this to the parameter list would break copy() and apply()
    * signatures for a generated jvm class.
    *
    * When breaking binary compatibility would be an option, consider moving this into a regular parameter list with adjusting
    * SchemaAnnotationsMacro(both scala 3 and 2) accordingly
    */
  private var _customise: Option[Schema[Any] => Schema[Any]] = None

  def withCustomise(c: Schema[Any] => Schema[Any]): SchemaAnnotations[T] = {
    val copy = this.copy[T]()
    copy._customise = Some(c)
    copy
  }

  /** Replaces the `copy` that would otherwise be generated for this case class, so that the `_customise` field - which is not a constructor
    * parameter - is carried over to the copy.
    *
    * The signature must stay exactly as the generated one would be. Scala 3 skips generating `copy` only when a user-defined `copy` matches
    * that signature; a differently shaped one becomes an overload instead, making every defaulted call ambiguous. Keeping the type
    * parameter named `T` also keeps the erased and generic signatures identical to the generated ones, which is what makes this binary
    * compatible. The casts below are needed because this `T` shadows the class's one - the generated `copy` is special-cased by the
    * compiler and needs no casts.
    *
    * Remove this method once `_customise` moves to a regular parameter list: the generated `copy` takes over again, and the explicit
    * `copy[T]()` in `withCustomise` can go back to `copy()`.
    */

  def copy[T](
      description: Option[String] = this.description,
      encodedExample: Option[Any] = this.encodedExample,
      default: Option[(T, Option[Any])] = this.default.asInstanceOf[Option[(T, Option[Any])]],
      format: Option[String] = this.format,
      deprecated: Option[Boolean] = this.deprecated,
      hidden: Option[Boolean] = this.hidden,
      encodedName: Option[String] = this.encodedName,
      validate: List[Validator[T]] = this.validate.asInstanceOf[List[Validator[T]]],
      validateEach: List[Validator[Any]] = this.validateEach
  ): SchemaAnnotations[T] = {
    val c = new SchemaAnnotations[T](
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
