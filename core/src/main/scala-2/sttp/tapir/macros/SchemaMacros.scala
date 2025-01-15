package sttp.tapir.macros

import magnolia1.Magnolia
import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto.SchemaMagnoliaDerivation
import sttp.tapir.internal.{ModifySchemaMacro, OneOfMacro, SchemaEnumerationMacro, SchemaMapMacro}

trait SchemaMacros[T] {

  /** Modifies nested schemas for case classes and case class families (sealed traits / enums), accessible with `path`, using the given
    * `modification` function. To traverse collections, use `.each`.
    *
    * Should only be used if the schema hasn't been created by `.map` ping another one. In such a case, the shape of the schema doesn't
    * correspond to the type `T`, but to some lower-level representation of the type.
    *
    * If the shape of the schema doesn't correspond to the path, the schema remains unchanged.
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

  /** Create a coproduct schema (e.g. for a `sealed trait`), where the value of the discriminator between child types is a read of a field
    * of the base type. The field, if not yet present, is added to each child schema.
    *
    * The schemas of the child types have to be provided explicitly with their value mappings in `mapping`.
    *
    * Note that if the discriminator value is some transformation of the child's type name (obtained using the implicit [[Configuration]]),
    * the coproduct schema can be derived automatically or semi-automatically.
    *
    * @param discriminatorSchema
    *   The schema that is used when adding the discriminator as a field to child schemas (if it's not yet in the schema).
    */
  def oneOfUsingField[E, V](extractor: E => V, asString: V => String)(
      mapping: (V, Schema[_])*
  )(implicit conf: Configuration, discriminatorSchema: Schema[V]): Schema[E] = macro OneOfMacro.generateOneOfUsingField[E, V]

  /** Create a coproduct schema for a `sealed trait` or `sealed abstract class`, where to discriminate between child types a wrapper product
    * is used. The name of the sole field in this product corresponds to the type's name, transformed using the implicit [[Configuration]].
    */
  def oneOfWrapped[E](implicit conf: Configuration): Schema[E] = macro OneOfMacro.generateOneOfWrapped[E]

  def derived[T]: Schema[T] = macro Magnolia.gen[T]

  /** Creates a schema for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that all subtypes of the sealed hierarchy `T` must be `object` s.
    *
    * This method cannot be implicit, as there's no way to constraint the type `T` to be a sealed trait / class enumeration, so that this
    * would be invoked only when necessary.
    */
  def derivedEnumeration[T]: CreateDerivedEnumerationSchema[T] = macro SchemaEnumerationMacro.derivedEnumeration[T]

  /** Creates a schema for an [[Enumeration]], where the validator is created using the enumeration's values. Unlike the default
    * [[derivedEnumerationValue]] method, which provides the schema implicitly, this variant allows customising how the schema is created.
    * This is useful if the low-level representation of the schema is different than a `String`, or if the enumeration's values should be
    * encoded in a different way than using `.toString`.
    *
    * Because of technical limitations of macros, the customisation arguments can't be given here directly, instead being delegated to
    * [[CreateDerivedEnumerationSchema]].
    */
  def derivedEnumerationValueCustomise[T <: scala.Enumeration#Value]: CreateDerivedEnumerationSchema[T] =
    macro SchemaEnumerationMacro.derivedEnumerationValueCustomise[T]

  /** Create a schema for an [[Enumeration]], where the validator is created using the enumeration's values. The low-level representation of
    * the enum is a `String`, and the enum values in the documentation will be encoded using `.toString`.
    */
  implicit def derivedEnumerationValue[T <: scala.Enumeration#Value]: Schema[T] = macro SchemaEnumerationMacro.derivedEnumerationValue[T]
}
