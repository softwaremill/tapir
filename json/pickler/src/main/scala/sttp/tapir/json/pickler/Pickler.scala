package sttp.tapir.json.pickler

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, ReaderConfig}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.Schema

import scala.annotation.implicitNotFound
import scala.collection.Factory
import scala.reflect.ClassTag

/** A pickler combines the [[Schema]] of a type (used for documentation and validation of deserialized values) with a jsoniter-scala
  * [[JsonValueCodec]]. Both are derived by a single macro expansion from a single [[PicklerConfiguration]], which is what guarantees that
  * the documented schema and the actual JSON encoding cannot drift apart.
  *
  * An in-scope pickler instance is required by `jsonBody` (and its variants), but it can also be converted to a codec explicitly using
  * [[toCodec]].
  */
@implicitNotFound(msg = """Could not summon a Pickler for type ${A}.
Picklers can be derived automatically by adding: `import sttp.tapir.json.pickler.generic.auto.*`, or manually using `Pickler.derived[A]`.
The latter is also useful for debugging derivation errors.""")
trait Pickler[A] {

  def schema: Schema[A]

  def codec: JsonValueCodec[A]

  /** Converts this pickler into a tapir JSON [[JsonCodec]], combining the derived schema with the derived jsoniter-scala codec.
    *
    * Decoding uses the jsoniter-scala `ReaderConfig` given in scope, if any.
    */
  final def toCodec(using readerConfig: ReaderConfig = Pickler.defaultReaderConfig): JsonCodec[A] =
    internal.runtime.PicklerUtils.toTapirCodec(codec, schema, readerConfig)

  /** A pickler for `Option[A]`: the schema is marked optional, `None` is written as `null`. */
  final def asOption: Pickler[Option[A]] =
    Pickler.fromSchemaAndCodec(schema.asOption, internal.runtime.CodecCombinators.option(codec))

  /** A pickler for a collection of `A`, written as a JSON array. */
  final def asIterable[C[X] <: Iterable[X]](using Factory[A, C[A]]): Pickler[C[A]] =
    Pickler.fromSchemaAndCodec(schema.asIterable[C], internal.runtime.CodecCombinators.iterable[A, C](codec))

  final def asArray(using ClassTag[A]): Pickler[Array[A]] =
    Pickler.fromSchemaAndCodec(schema.asArray, internal.runtime.CodecCombinators.array(codec))
}

object Pickler {

  /** Derive a [[Pickler]] instance for `A` at compile time.
    *
    * Can be used explicitly, in the definition of a `given`, or indirectly via a `... derives Pickler` clause. It is deliberately not a
    * `given` itself: automatic derivation is opt-in through `import sttp.tapir.json.pickler.generic.auto.*`.
    */
  inline def derived[A](using inline config: PicklerConfiguration): Pickler[A] =
    ${ internal.compiletime.PicklerMacros.derivePicklerImpl[A]('config) }

  /** Derive only the tapir [[Schema]] for `A`, without building a codec.
    *
    * This is the entry point used by the schema-focused regression tests, and is also useful on its own when a type is only ever
    * documented, never serialized.
    */
  inline def schemaFor[A](using inline config: PicklerConfiguration): Schema[A] =
    ${ internal.compiletime.PicklerMacros.deriveSchemaOnlyImpl[A]('config) }

  /** Create a pickler for a sealed hierarchy `T`, where the discriminator value of each child is not derived from its type name but decided
    * by the user: the value of `extractorFn` applied to the child, rendered with `asStringFn`.
    *
    * The children have to be listed explicitly, each with the value that selects it:
    * {{{
    * Pickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(
    *   200 -> Pickler.derived[StatusOk],
    *   400 -> Pickler.derived[StatusBadRequest]
    * )
    * }}}
    *
    * The mapping keys and `asStringFn` must be evaluable at compile time (literals and lambdas over them), because the resulting
    * discriminator values become part of the generated jsoniter codec. The children's *schemas* are taken from the given picklers; their
    * *codecs* are derived here with the overridden discriminator values.
    */
  inline def oneOfUsingField[T, V](inline extractorFn: T => V, inline asStringFn: V => String)(
      inline mapping: (V, Pickler[? <: T])*
  )(using inline config: PicklerConfiguration): Pickler[T] =
    ${ internal.compiletime.PicklerMacros.oneOfUsingFieldImpl[T, V]('extractorFn, 'asStringFn, 'mapping, 'config) }

  /** Create a pickler for an enumeration: a sealed hierarchy or `enum` whose cases are all singletons (parameterised `enum` cases
    * included). The returned builder chooses how the cases are rendered:
    * {{{
    * Pickler.derivedEnumeration[Color].defaultStringBased            // as Pickler.derived[Color] would
    * Pickler.derivedEnumeration[Color].customStringBased(_.ordinal.toString)
    * }}}
    * The custom `encode` function is applied at runtime, so it is not restricted to compile-time evaluable code.
    */
  inline def derivedEnumeration[T](using inline config: PicklerConfiguration): CreateDerivedEnumerationPickler[T] =
    ${ internal.compiletime.PicklerMacros.derivedEnumerationImpl[T]('config) }

  /** Create a pickler for a map with arbitrary keys. Keys are rendered with `keyToString` and parsed back with `stringToKey`; the schema
    * documents them through the same `keyToString`, as `Schema.schemaForMap` does. Values use the given pickler.
    *
    * Maps with `String` keys need none of this: they are derived directly. To make this pickler available for automatic derivation of
    * enclosing types, define it as a `given`, e.g.:
    * {{{
    * given Pickler[Map[UUID, Book]] = Pickler.picklerForMap(_.toString, UUID.fromString)
    * }}}
    */
  // `inline` so that `Schema.schemaForMap` (a macro) sees the concrete `K` and `V` at the call site and names the schema after them,
  // rather than after the abstract type parameters `picklerForMap.K` / `picklerForMap.V`.
  inline def picklerForMap[K, V](keyToString: K => String, stringToKey: String => K)(using pv: Pickler[V]): Pickler[Map[K, V]] = {
    given Schema[V] = pv.schema
    fromSchemaAndCodec(
      Schema.schemaForMap[K, V](keyToString),
      internal.runtime.CodecCombinators.map(keyToString, stringToKey, pv.codec)
    )
  }

  /** Used for decoding unless a `given ReaderConfig` is in scope. A custom config should extend this one, so that decode errors (which can
    * be returned to clients) don't include a hex dump of the input.
    */
  val defaultReaderConfig: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  /** A pickler over an existing schema and jsoniter-scala codec, for types whose JSON representation is hand-written (or comes from
    * elsewhere) rather than derived. Put the result in a `given` and both derivation halves honour it wherever the type is nested. Keeping
    * the two halves in step is the caller's responsibility here.
    */
  def fromSchemaAndCodec[A](schema: Schema[A], codec: JsonValueCodec[A]): Pickler[A] =
    internal.runtime.PicklerFactories.instance(schema, codec)

  /** Marker type: if an implicit instance is in scope, the macro will log its derivation process.
    *
    * @see
    *   [[sttp.tapir.json.pickler.debug.logDerivationForPickler]]
    */
  sealed trait LogDerivation
  object LogDerivation extends LogDerivation
}
