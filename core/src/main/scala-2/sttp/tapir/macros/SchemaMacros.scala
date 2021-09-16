package sttp.tapir.macros

import magnolia.Magnolia
import sttp.tapir.{Schema, SchemaType, Validator}
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.internal.{OneOfMacro, SchemaMagnoliaDerivation, SchemaMapMacro}
import sttp.tapir.internal.{ModifySchemaMacro, SchemaEnumerationMacro}

import scala.reflect.ClassTag

trait SchemaMacros[T] {
  def modify[U](path: T => U)(modification: Schema[U] => Schema[U]): Schema[T] = macro ModifySchemaMacro.generateModify[T, U]
}

trait SchemaCompanionMacros extends SchemaMagnoliaDerivation {
  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = macro SchemaMapMacro.generateSchemaForStringMap[V]

  /** Create a schema for a map with arbitrary keys. The schema for the keys (`Schema[K]`) should be a string (that is, the schema type
    * should be [[sttp.tapir.SchemaType.SString]]), however this cannot be verified at compile-time and is not verified at run-time.
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
  def schemaForMap[K: Schema, V: Schema](keyToString: K => String): Schema[Map[K, V]] =
    macro SchemaMapMacro.generateSchemaForMap[K, V]

  def oneOfUsingField[E, V](extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*)(implicit conf: Configuration): Schema[E] =
    macro OneOfMacro.generateOneOfUsingField[E, V]
  def derived[T]: Schema[T] = macro Magnolia.gen[T]

  /** Creates a schema for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that all subtypes of the sealed hierarchy `T` must be `object`s.
    *
    * Because of technical limitations of macros, the customisation arguments can't be given here directly, instead being delegated to
    * [[CreateDerivedEnumerationSchema]].
    */
  def derivedEnumeration[T]: CreateDerivedEnumerationSchema[T] = macro SchemaEnumerationMacro.derivedEnumeration[T]
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

    val s0 = Schema.string.validate(v)
    default.fold(s0)(d => s0.default(d, encode.map(e => e(d))))
  }
}
