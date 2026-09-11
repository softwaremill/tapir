package sttp.tapir.json.pickler.generic

import sttp.tapir.json.pickler.{Pickler, PicklerConfiguration}

/** Import `sttp.tapir.json.pickler.generic.auto.*` for automatic pickler derivation: a [[Pickler]] will be derived at the use site for
  * every type that does not already have one in the given/implicit scope.
  *
  * Unlike the uPickle-based module this replaces, there is no `Mirror.Of[T]` requirement — the derivation handles primitives, collections
  * and `Map`s as well as product and sum types, so constraining it to Mirrors would exclude exactly the types that need no user-visible
  * ceremony.
  */
object auto {

  /** `config` is an `inline` parameter on purpose: the derivation must evaluate the configuration at compile time (see
    * `PicklerMacrosImpl.foldConfiguration`), which needs the *reference to the given*, not a proxy `val` holding it.
    */
  inline implicit def picklerForType[T](implicit inline config: PicklerConfiguration): Pickler[T] = Pickler.derived[T]
}
