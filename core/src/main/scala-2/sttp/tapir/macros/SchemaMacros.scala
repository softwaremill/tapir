package sttp.tapir.macros

import magnolia1.Magnolia
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto.SchemaMagnoliaDerivation
import sttp.tapir.internal.{ModifySchemaMacro, OneOfMacro, SchemaEnumerationMacro, SchemaMapMacro}
import sttp.tapir.{Schema, SchemaType, Validator}

trait SchemaMacros[T] {

  /** Modifies nested schemas for case classes and case class families (sealed traits / enums), accessible with `path`, using the given
    * `modification` function. To traverse collections, use `.each`.
    */
  def modify[U](path: T => U)(modification: Schema[U] => Schema[U]): Schema[T] = macro ModifySchemaMacro.generateModify[T, U]
}

trait SchemaCompanionMacros extends SchemaMagnoliaDerivation {
  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = macro SchemaMapMacro.generateSchemaForStringMap[V]

  /** Create a schema for a map with arbitrary keys. The schema for the keys `K` should be a string, however this cannot be verified at
    * compile-time and is not verified at run-time.
    *
    * The given `keyToString` conversion function is used during validation.
    *
    * If you'd like this schema to be available as an implicit for a given type of keys, create an custom implicit, e.g.:
    *
    * {{{
    * case class MyKey(value: String) extends AnyVal
    * implicit val schemaForMyMap = Schema.schemaForMap[MyKey, MyValue](_.value)
    * }}}
    */
  def schemaForMap[K, V: Schema](keyToString: K => String): Schema[Map[K, V]] =
    macro SchemaMapMacro.generateSchemaForMap[K, V]

  /** @param discriminatorSchema
    *   The schema that is used when adding the discriminator as a field to child schemas (if it's not yet in the schema).
    */
  def oneOfUsingField[E, V](extractor: E => V, asString: V => String)(
      mapping: (V, Schema[_])*
  )(implicit conf: Configuration, discriminatorSchema: Schema[V]): Schema[E] =
    macro OneOfMacro.generateOneOfUsingField[E, V]

  def derived[T]: Schema[T] = macro Magnolia.gen[T]

  /** Creates a schema for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that all subtypes of the sealed hierarchy `T` must be `object`s.
    *
    * Because of technical limitations of macros, the customisation arguments can't be given here directly, instead being delegated to
    * [[CreateDerivedEnumerationSchema]].
    */
  def derivedEnumeration[T]: CreateDerivedEnumerationSchema[T] = macro SchemaEnumerationMacro.derivedEnumeration[T]

  /** Create a schema for scala `Enumeration` and the `Validator` instance based on possible enumeration values */
  implicit def derivedEnumerationValue[T <: scala.Enumeration#Value]: Schema[T] = macro SchemaEnumerationMacro.derivedEnumerationValue[T]
}

class CreateDerivedEnumerationSchema[T](validator: Validator.Enumeration[T]) {

  /** @param encode
    *   Specify how values of this type can be encoded to a raw value (typically a [[String]]; the raw form should correspond with
    *   `schemaType`). This encoding will be used when generating documentation.
    * @param schemaType
    *   The low-level representation of the enumeration. Defaults to a string.
    */
  def apply(
      encode: Option[T => Any] = None,
      schemaType: SchemaType[T] = SchemaType.SString[T](),
      default: Option[T] = None
  ): Schema[T] = {
    val v = encode.fold(validator)(e => validator.encode(e))

    val s0 = Schema(schemaType).validate(v)
    default.fold(s0)(d => s0.default(d, encode.map(e => e(d))))
  }
}
