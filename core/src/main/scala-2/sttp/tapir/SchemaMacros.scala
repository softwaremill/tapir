package sttp.tapir

import magnolia.Magnolia
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.internal.OneOfMacro.oneOfMacro
import sttp.tapir.generic.internal.{SchemaMagnoliaDerivation, SchemaMapMacro}
import sttp.tapir.internal.ModifySchemaMacro

trait SchemaMacros[T] {
  def modify[U](path: T => U)(modification: Schema[U] => Schema[U]): Schema[T] = macro ModifySchemaMacro.modifyMacro[T, U]
}

trait SchemaCompanionMacros extends SchemaMagnoliaDerivation {
  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = macro SchemaMapMacro.schemaForMap[V]

  def oneOfUsingField[E, V](extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*)(implicit conf: Configuration): Schema[E] =
    macro oneOfMacro[E, V]
  def derived[T]: Schema[T] = macro Magnolia.gen[T]
}
