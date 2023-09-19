package sttp.tapir.generic.auto

import sttp.tapir.Schema
import sttp.tapir.generic.Configuration
import sttp.tapir.macros.SchemaMacroDerivation

import scala.deriving.Mirror

trait SchemaDerivation extends SchemaMacroDerivation:
  inline implicit def schemaForCaseClass[T](implicit m: Mirror.Of[T], cfg: Configuration): Schema[T] = derived[T]
