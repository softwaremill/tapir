package sttp.tapir.generic

import sttp.tapir.Schema

package object auto extends SchemaDerivation

trait SchemaDerivation {
  implicit def schemaForCaseClass[T]: Derived[Schema[T]] = ??? // TODO
}
