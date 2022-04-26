package sttp.tapir.generic.auto

import sttp.tapir.Schema
import sttp.tapir.generic.Derived
import sttp.tapir.generic.internal.MagnoliaDerivedMacro

trait SchemaDerivation extends SchemaMagnoliaDerivation {
  implicit def schemaForCaseClass[T]: Derived[Schema[T]] = macro MagnoliaDerivedMacro.generateDerivedGen[T]
}
