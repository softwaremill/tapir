package sttp.tapir.json.pickler.generic

import scala.reflect.ClassTag
import scala.deriving.Mirror
import sttp.tapir.generic.Configuration
import sttp.tapir.json.pickler.Pickler

object auto {
  inline implicit def picklerForCaseClass[T: ClassTag](implicit m: Mirror.Of[T], c: Configuration): Pickler[T] = Pickler.derived[T]
}
