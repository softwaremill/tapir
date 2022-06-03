package sttp.tapir.generic.auto

import sttp.tapir.Schema
import sttp.tapir.generic.{Configuration, Derived}

import scala.deriving.Mirror

trait SchemaDerivation extends SchemaMagnoliaDerivation:
  inline implicit def schemaForCaseClass[T](implicit m: Mirror.Of[T], cfg: Configuration): Derived[Schema[T]] = Derived(derived[T])
