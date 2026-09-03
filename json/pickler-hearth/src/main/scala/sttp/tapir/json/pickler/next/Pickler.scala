package sttp.tapir.json.pickler.next

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.Schema

import scala.annotation.implicitNotFound

/** A pickler combines the [[Schema]] of a type (used for documentation and validation of deserialized values) with a
  * jsoniter-scala [[JsonValueCodec]]. Both are derived by a single macro expansion from a single
  * [[PicklerConfiguration]], which is what guarantees that the documented schema and the actual JSON encoding cannot
  * drift apart.
  *
  * An in-scope pickler instance is required by `jsonBody` (and its variants), but it can also be converted to a codec
  * explicitly using [[toCodec]].
  */
@implicitNotFound(msg = """Could not summon a Pickler for type ${A}.
Picklers can be derived automatically by adding: `import sttp.tapir.json.pickler.next.generic.auto.*`, or manually using `Pickler.derived[A]`.
The latter is also useful for debugging derivation errors.""")
trait Pickler[A] {

  def schema: Schema[A]

  def codec: JsonValueCodec[A]

  /** Converts this pickler into a tapir JSON [[JsonCodec]], combining the derived schema with the derived
    * jsoniter-scala codec.
    */
  final def toCodec: JsonCodec[A] = internal.runtime.PicklerUtils.toTapirCodec(codec, schema)
}

object Pickler extends PicklerCompanionCompat {

  /** Marker type: if an implicit instance is in scope, the macro will log its derivation process.
    *
    * @see
    *   [[sttp.tapir.json.pickler.next.debug.logDerivationForPickler]]
    */
  sealed trait LogDerivation
  object LogDerivation extends LogDerivation
}
