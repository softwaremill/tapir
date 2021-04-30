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
  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = macro SchemaMapMacro.generateSchemaForMap[V]

  def oneOfUsingField[E, V](extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*)(implicit conf: Configuration): Schema[E] =
    macro OneOfMacro.generateOneOfUsingField[E, V]
  def derived[T]: Schema[T] = macro Magnolia.gen[T]
}
