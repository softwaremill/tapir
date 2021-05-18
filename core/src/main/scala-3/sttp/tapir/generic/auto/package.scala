package sttp.tapir.generic

import sttp.tapir.Schema
import sttp.tapir.internal.SchemaMagnoliaDerivation
import scala.deriving.Mirror

package object auto extends SchemaDerivation

trait SchemaDerivation extends SchemaMagnoliaDerivation {
  inline implicit def schemaForCaseClass[T](using Mirror.ProductOf[T]): Derived[Schema[T]] = Derived(derived[T])
}
