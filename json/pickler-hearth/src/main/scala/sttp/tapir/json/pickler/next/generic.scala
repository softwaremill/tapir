package sttp.tapir.json.pickler.next.generic

import sttp.tapir.json.pickler.next.{Pickler, PicklerConfiguration}

/** Import `sttp.tapir.json.pickler.next.generic.auto.*` for automatic pickler derivation: a [[Pickler]] will be
  * derived at the use site for every type that does not already have one in the given/implicit scope.
  *
  * Unlike the uPickle-based module this replaces, there is no `Mirror.Of[T]` requirement — the derivation handles
  * primitives, collections and `Map`s as well as product and sum types, so constraining it to Mirrors would exclude
  * exactly the types that need no user-visible ceremony.
  */
object auto {
  inline implicit def picklerForType[T](implicit config: PicklerConfiguration): Pickler[T] = Pickler.derived[T]
}
