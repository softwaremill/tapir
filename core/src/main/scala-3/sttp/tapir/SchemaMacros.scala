package sttp.tapir

import sttp.tapir.generic.Configuration

trait SchemaMacros[T] {
  def modify[U](path: T => U)(modification: Schema[U] => Schema[U]): Schema[T] = ??? // TODO
}

trait SchemaCompanionMacros {
  implicit def schemaForMap[V: Schema]: Schema[Map[String, V]] = ??? // TODO

  def oneOfUsingField[E, V](extractor: E => V, asString: V => String)(mapping: (V, Schema[_])*)(implicit conf: Configuration): Schema[E] =
    ??? // TODO
  def derived[T]: Schema[T] = ??? // TODO
}
