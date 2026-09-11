package sttp.tapir.json.pickler

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.{Schema, Validator}
import sttp.tapir.json.pickler.internal.runtime.{CodecCombinators, PicklerFactories}

/** Builder returned by [[Pickler.derivedEnumeration]]: a pickler for an enumeration (a sealed hierarchy or `enum` whose cases are all
  * singletons), with a choice of how the cases are rendered as strings.
  *
  * Instances are created by the derivation macro, which supplies the singleton values and the default schema and codec — the ones
  * [[Pickler.derived]] would produce for the same type and configuration.
  */
final class CreateDerivedEnumerationPickler[T] private[pickler] (
    values: List[T],
    defaultSchema: Schema[T],
    defaultCodec: JsonValueCodec[T]
) {

  /** Each case is rendered by the configured `toDiscriminatorValue` — the same as [[Pickler.derived]] does for an enumeration, so this is
    * only ever needed for symmetry with [[customStringBased]].
    */
  def defaultStringBased: Pickler[T] = PicklerFactories.instance(defaultSchema, defaultCodec)

  /** Each case is rendered by `encode`, in the JSON and in the documentation alike. `encode` must give distinct strings to distinct cases;
    * a collision fails immediately, not on first decode.
    */
  def customStringBased(encode: T => String): Pickler[T] = {
    val codec = CodecCombinators.stringEnum(values, encode)
    val schema = defaultSchema.copy(validator = Validator.enumeration(values, (v: T) => Some(encode(v))))
    PicklerFactories.instance(schema, codec)
  }
}
