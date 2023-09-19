package sttp.tapir.json.pickler.generic

import scala.reflect.ClassTag
import scala.deriving.Mirror
import sttp.tapir.generic.Configuration
import sttp.tapir.json.pickler.Pickler

/** Import `sttp.tapir.json.pickler.auto.*`` for automatic generic pickler derivation. A [[Pickler]] will be derived at the usage side using
  * [[Pickler.derived]] for each type where a given `Pickler` is not available in the current given/implicit scope.
  */
object auto {
  inline implicit def picklerForCaseClass[T: ClassTag](implicit m: Mirror.Of[T], c: Configuration): Pickler[T] = Pickler.derived[T]
}
