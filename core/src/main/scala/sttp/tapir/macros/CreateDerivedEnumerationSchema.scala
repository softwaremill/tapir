package sttp.tapir.macros

import sttp.tapir.{Schema, SchemaAnnotations, SchemaType, Validator}

class CreateDerivedEnumerationSchema[T](validator: Validator.Enumeration[T], schemaAnnotations: SchemaAnnotations[T]) {

  // needed for binary compatibility
  def this(validator: Validator.Enumeration[T]) = this(validator, SchemaAnnotations.empty)

  /** @param encode
    *   Specify how values of this type can be encoded to a raw value (typically a [[String]]; the raw form should correspond with
    *   `schemaType`). This encoding will be used when generating documentation. Defaults to an identity function, which effectively mean
    *   that `.toString` will be used to represent the enumeration in the docs.
    * @param schemaType
    *   The low-level representation of the enumeration. Defaults to a string.
    */
  def apply(
      encode: Option[T => Any] = Some(v => v),
      schemaType: SchemaType[T] = SchemaType.SString[T](),
      default: Option[T] = None
  ): Schema[T] = {
    val v = encode.fold(validator)(e => validator.encode(e))

    val s0 = Schema(schemaType).validate(v)
    val s1 = default.fold(s0)(d => s0.default(d, encode.map(e => e(d))))

    schemaAnnotations.enrich(s1)
  }

  /** Creates the schema assuming the low-level representation is a `String`. The encoding function passes the object unchanged (which means
    * `.toString` will be used to represent the enumeration in documentation).
    */
  def defaultStringBased: Schema[T] = apply()
}
