package sttp.tapir.json.pickler

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
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
    */
  final def toCodec: JsonCodec[A] = internal.runtime.PicklerUtils.toTapirCodec(codec, schema)

  /** A pickler for `Option[A]`: the schema is marked optional, `None` is written as `null`. */
  final def asOption: Pickler[Option[A]] =
    internal.runtime.PicklerFactories.instance(schema.asOption, internal.runtime.CodecCombinators.option(codec))

  /** A pickler for a collection of `A`, written as a JSON array. */
  final def asIterable[C[X] <: Iterable[X]](using Factory[A, C[A]]): Pickler[C[A]] =
    internal.runtime.PicklerFactories.instance(schema.asIterable[C], internal.runtime.CodecCombinators.iterable[A, C](codec))

  final def asArray(using ClassTag[A]): Pickler[Array[A]] =
    internal.runtime.PicklerFactories.instance(schema.asArray, internal.runtime.CodecCombinators.array(codec))
}

object Pickler extends PicklerCompanionCompat {

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
