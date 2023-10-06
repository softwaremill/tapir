package sttp.tapir.json.pickler.generic

import sttp.tapir.json.pickler.Pickler
import sttp.tapir.json.pickler.PicklerConfiguration

import scala.reflect.ClassTag
import scala.deriving.Mirror

/** Import `sttp.tapir.json.pickler.auto.*`` for automatic generic pickler derivation. A [[Pickler]] will be derived at the usage side using
  * [[Pickler.derived]] for each type where a given `Pickler` is not available in the current given/implicit scope.
  */
object auto {
  inline implicit def picklerForCaseClass[T: ClassTag](implicit m: Mirror.Of[T], c: PicklerConfiguration): Pickler[T] = Pickler.derived[T]
}
