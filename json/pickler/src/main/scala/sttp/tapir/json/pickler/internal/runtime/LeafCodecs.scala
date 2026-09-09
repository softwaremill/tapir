package sttp.tapir.json.pickler.internal.runtime

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}

import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}

/** Codecs for leaf types that tapir has a `Schema` for but `JsonCodecMaker` does not derive on its own.
  *
  * The derivation macro puts these into scope (as `implicit lazy val`s in the generated block) whenever the type graph contains one of the
  * types, so that jsoniter picks them up through its ordinary implicit lookup.
  */
object LeafCodecs {

  val javaBigDecimal: JsonValueCodec[JBigDecimal] = new JsonValueCodec[JBigDecimal] {
    def nullValue: JBigDecimal = null
    def decodeValue(in: JsonReader, default: JBigDecimal): JBigDecimal = {
      val d = in.readBigDecimal(null)
      if (d eq null) default else d.bigDecimal
    }
    def encodeValue(x: JBigDecimal, out: JsonWriter): Unit = out.writeVal(BigDecimal(x))
  }

  val javaBigInteger: JsonValueCodec[JBigInteger] = new JsonValueCodec[JBigInteger] {
    def nullValue: JBigInteger = null
    def decodeValue(in: JsonReader, default: JBigInteger): JBigInteger = {
      val i = in.readBigInt(null)
      if (i eq null) default else i.bigInteger
    }
    def encodeValue(x: JBigInteger, out: JsonWriter): Unit = out.writeVal(BigInt(x))
  }
}
