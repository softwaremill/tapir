package sttp.tapir.generic.auto

import sttp.tapir.generic.internal.SchemaMagnoliaDerivation
import sttp.tapir.generic.Derived
import sttp.tapir.generic.internal.MagnoliaDerivedMacro
import sttp.tapir.Schema

package object schema extends SchemaDerivation

trait SchemaDerivation extends SchemaMagnoliaDerivation {
    implicit def schemaForCaseClass[T]: Derived[Schema[T]] = macro MagnoliaDerivedMacro.derivedGen[T]
}