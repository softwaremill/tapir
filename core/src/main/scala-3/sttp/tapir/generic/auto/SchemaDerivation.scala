package sttp.tapir.generic.auto

import sttp.tapir.Schema
import sttp.tapir.generic.{Configuration, Derived}

import scala.deriving.Mirror
import scala.reflect.ClassTag

trait SchemaDerivation extends SchemaMagnoliaDerivation:
  inline implicit def schemaForCaseClass[T](implicit m: Mirror.Of[T], cfg: Configuration, ct: ClassTag[T]): Derived[Schema[T]] = Derived(
    derived[T]
  )
