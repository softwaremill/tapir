package sttp.tapir.generic

import sttp.tapir.Schema

package object auto extends SchemaDerivation

trait SchemaDerivation extends SchemaMagnoliaDerivation {
  inline implicit def schemaForCaseClass[T](implicit m: Mirror.Of[T], cfg: Configuration): Derived[Schema[T]] = Derived(derived[T])
}
