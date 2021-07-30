package sttp.tapir.macros

import magnolia.Magnolia
import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.internal.{OneOfMacro, SchemaMagnoliaDerivation, SchemaMapMacro}
import sttp.tapir.internal.ModifySchemaMacro

trait SchemaMacros[T] {
  def modify[U](path: T => U)(modification: Schema[U] => Schema[U]): Schema[T] = macro ModifySchemaMacro.generateModify[T, U]
}

trait SchemaCompanionMacros extends SchemaMagnoliaDerivation {
  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = macro SchemaMapMacro.generateSchemaForStringMap[V]

  /** Create a schema for a map with arbitrary keys. The schema for the keys (`Schema[K]`) should be a string (that is,
    * the schema type should be [[sttp.tapir.SchemaType.SString]]), however this cannot be verified at compile-time
    * and is not verified at run-time.
    *
    * The given `keyToString` conversion function is used during validation.
    *
    * If you'd like this schema to be available as an implicit for a given type of keys, create an custom implicit,
    * e.g.:
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
}
