package sttp.tapir.json.pickler.next.internal.runtime

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import sttp.tapir.Schema
import sttp.tapir.json.pickler.next.Pickler

/** Factory for the final [[Pickler]] instance emitted by the derivation macro.
  *
  * The macro deliberately emits a call to [[instance]] with lambdas, rather than expanding a
  * `new Pickler[A] { ... }` literal at each derivation site. Every anonymous class expansion would produce a separate
  * `.class` file per derived type; lambdas compile to `invokedynamic` and produce none. The two anonymous classes below
  * are defined once, here, and shared by every derived pickler.
  */
object PicklerFactories {

  def instance[A](
      schemaValue: Schema[A],
      nullValueForCodec: A,
      decodeFn: (JsonReader, A) => A,
      encodeFn: (A, JsonWriter) => Unit
  ): Pickler[A] = new Pickler[A] {
    val schema: Schema[A] = schemaValue
    val codec: JsonValueCodec[A] = new JsonValueCodec[A] {
      def nullValue: A = nullValueForCodec
      def decodeValue(in: JsonReader, default: A): A = decodeFn(in, default)
      def encodeValue(x: A, out: JsonWriter): Unit = encodeFn(x, out)
    }
  }
}
