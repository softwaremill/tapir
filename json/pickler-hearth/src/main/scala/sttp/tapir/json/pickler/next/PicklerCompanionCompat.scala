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
    * Can be used explicitly, in the definition of a `given`, or indirectly via a `... derives Pickler` clause.
    */
  inline given derived[A](using inline config: PicklerConfiguration): Pickler[A] =
    ${ internal.compiletime.PicklerMacros.derivePicklerImpl[A]('config) }

  /** Derive only the tapir [[Schema]] for `A`, without building a codec.
    *
    * This is the entry point used by the schema-focused regression tests, and is also useful on its own when a type is
    * only ever documented, never serialized.
    */
  inline def schemaFor[A](using inline config: PicklerConfiguration): Schema[A] =
    ${ internal.compiletime.PicklerMacros.deriveSchemaOnlyImpl[A]('config) }
}
