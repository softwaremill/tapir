package sttp.tapir.generic

import sttp.tapir.Schema
import sttp.tapir.SchemaType.SString
import sttp.tapir.generic.internal.{MagnoliaDerivedMacro, SchemaMagnoliaDerivation}

package object auto extends SchemaDerivation

trait SchemaDerivation extends SchemaMagnoliaDerivation {
  implicit def schemaForCaseClass[T]: Derived[Schema[T]] = macro MagnoliaDerivedMacro.generateDerivedGen[T]

  implicit def schemaForEnumeration[T <: scala.Enumeration#Value]: Schema[T] = Schema(SString())
}
