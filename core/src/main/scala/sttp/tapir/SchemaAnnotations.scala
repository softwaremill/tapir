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
    *
    * This has to be called last within macro transformations otherwise it would be lost as it is not a part of the auto generated copy
    */
  private var _customise: Option[Schema[Any] => Schema[Any]] = None

  def withCustomise(c: Schema[Any] => Schema[Any]): SchemaAnnotations[T] = {
    val copy = this.copy()
    copy._customise = Some(c)
    copy
  }
}

object SchemaAnnotations extends SchemaAnnotationsMacros {
  def empty[T]: SchemaAnnotations[T] = SchemaAnnotations(None, None, None, None, None, None, None, Nil, Nil)
}
