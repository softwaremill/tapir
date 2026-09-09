package sttp.tapir.json.pickler.next

import sttp.tapir.Schema

/** Scala 3 entry points for [[Pickler]] derivation.
  *
  * This lives in its own trait (rather than directly in the `Pickler` companion) so that adding Scala 2.13 support
  * later is a matter of moving this file to `src/main/scala-3` and adding a `src/main/scala-2` counterpart with
  * `implicit def ... = macro ...` definitions. The shared macro logic in `internal.compiletime` stays untouched.
  */
private[next] trait PicklerCompanionCompat { this: Pickler.type =>

  /** Derive a [[Pickler]] instance for `A` at compile time.
    *
    * Can be used explicitly, in the definition of a `given`, or indirectly via a `... derives Pickler` clause. It is
    * deliberately not a `given` itself (as in the uPickle-based module): automatic derivation is opt-in through
    * `import sttp.tapir.json.pickler.next.generic.auto.*`.
    */
  inline def derived[A](using inline config: PicklerConfiguration): Pickler[A] =
    ${ internal.compiletime.PicklerMacros.derivePicklerImpl[A]('config) }

  /** Derive only the tapir [[Schema]] for `A`, without building a codec.
    *
    * This is the entry point used by the schema-focused regression tests, and is also useful on its own when a type is
    * only ever documented, never serialized.
    */
  inline def schemaFor[A](using inline config: PicklerConfiguration): Schema[A] =
    ${ internal.compiletime.PicklerMacros.deriveSchemaOnlyImpl[A]('config) }

  /** Create a pickler for a sealed hierarchy `T`, where the discriminator value of each child is not derived from its
    * type name but decided by the user: the value of `extractorFn` applied to the child, rendered with `asStringFn`.
    *
    * The children have to be listed explicitly, each with the value that selects it:
    * {{{
    * Pickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(
    *   200 -> Pickler.derived[StatusOk],
    *   400 -> Pickler.derived[StatusBadRequest]
    * )
    * }}}
    *
    * The mapping keys and `asStringFn` must be evaluable at compile time (literals and lambdas over them), because the
    * resulting discriminator values become part of the generated jsoniter codec. The children's *schemas* are taken
    * from the given picklers; their *codecs* are derived here with the overridden discriminator values.
    */
  inline def oneOfUsingField[T, V](inline extractorFn: T => V, inline asStringFn: V => String)(
      inline mapping: (V, Pickler[? <: T])*
  )(using inline config: PicklerConfiguration): Pickler[T] =
    ${ internal.compiletime.PicklerMacros.oneOfUsingFieldImpl[T, V]('extractorFn, 'asStringFn, 'mapping, 'config) }

  /** Create a pickler for an enumeration: a sealed hierarchy or `enum` whose cases are all singletons (parameterised
    * `enum` cases included). The returned builder chooses how the cases are rendered:
    * {{{
    * Pickler.derivedEnumeration[Color].defaultStringBased            // as Pickler.derived[Color] would
    * Pickler.derivedEnumeration[Color].customStringBased(_.ordinal.toString)
    * }}}
    * The custom `encode` function is applied at runtime, so it is not restricted to compile-time evaluable code.
    */
  inline def derivedEnumeration[T](using inline config: PicklerConfiguration): CreateDerivedEnumerationPickler[T] =
    ${ internal.compiletime.PicklerMacros.derivedEnumerationImpl[T]('config) }

  /** Create a pickler for a map with arbitrary keys. Keys are rendered with `keyToString` and parsed back with
    * `stringToKey`; the schema documents them through the same `keyToString`, as `Schema.schemaForMap` does. Values use
    * the given pickler.
    *
    * Maps with `String` keys need none of this: they are derived directly. To make this pickler available for automatic
    * derivation of enclosing types, define it as a `given`, e.g.:
    * {{{
    * given Pickler[Map[UUID, Book]] = Pickler.picklerForMap(_.toString, UUID.fromString)
    * }}}
    */
  inline def picklerForMap[K, V](keyToString: K => String, stringToKey: String => K)(using pv: Pickler[V]): Pickler[Map[K, V]] = {
    given Schema[V] = pv.schema
    internal.runtime.PicklerFactories.instance(
      Schema.schemaForMap[K, V](keyToString),
      internal.runtime.CodecCombinators.map(keyToString, stringToKey, pv.codec)
    )
  }
}
