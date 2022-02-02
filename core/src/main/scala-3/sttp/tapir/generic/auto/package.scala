package sttp.tapir.generic

import sttp.tapir.Schema
import sttp.tapir.SchemaType.SString
import sttp.tapir.internal.SchemaMagnoliaDerivation

import scala.deriving.Mirror

package object auto extends SchemaDerivation

trait SchemaDerivation extends SchemaMagnoliaDerivation {
  inline implicit def schemaForCaseClass[T](implicit m: Mirror.Of[T], cfg: Configuration): Derived[Schema[T]] = Derived(derived[T])

  inline implicit def schemaForEnum[T <: scala.reflect.Enum]: Schema[T] = Schema(SString())
}
