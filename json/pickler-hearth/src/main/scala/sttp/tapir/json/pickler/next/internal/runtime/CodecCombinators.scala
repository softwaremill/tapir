package sttp.tapir.json.pickler.next.internal.runtime

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArrayReentrant, JsonReader, JsonReaderException, JsonValueCodec, JsonWriter}

import scala.collection.Factory
import scala.reflect.ClassTag

/** Hand-written `JsonValueCodec` combinators, for the shapes `JsonCodecMaker` either cannot produce or cannot produce from a codec that
  * already exists at runtime.
  *
  * Two kinds of callers:
  *   - the `Pickler` facade (`asOption`, `asIterable`, `asArray`, `picklerForMap`, `derivedEnumeration`), which wraps a codec it already
  *     holds — `JsonCodecMaker.make` cannot help there because it runs at compile time;
  *   - the derivation macro, for `Either`, which jsoniter has no encoding for.
  *
  * Token handling follows the code `JsonCodecMaker` generates for the same shapes, so that the JSON accepted and produced here is
  * indistinguishable from jsoniter's own.
  */
object CodecCombinators {

  /** `None` is `null`; anything else is the inner value. Matches jsoniter's handling of a standalone `Option`. */
  def option[T](inner: JsonValueCodec[T]): JsonValueCodec[Option[T]] = new JsonValueCodec[Option[T]] {
    def nullValue: Option[T] = None
    def decodeValue(in: JsonReader, default: Option[T]): Option[T] =
      if (in.isNextToken('n')) in.readNullOrError(default, "expected value or null")
      else {
        in.rollbackToken()
        Some(inner.decodeValue(in, inner.nullValue))
      }
    def encodeValue(x: Option[T], out: JsonWriter): Unit = x match {
      case Some(v) => inner.encodeValue(v, out)
      case None    => out.writeNull()
    }
  }

  def iterable[T, C[X] <: Iterable[X]](inner: JsonValueCodec[T])(implicit factory: Factory[T, C[T]]): JsonValueCodec[C[T]] =
    new JsonValueCodec[C[T]] {
      def nullValue: C[T] = factory.newBuilder.result()
      def decodeValue(in: JsonReader, default: C[T]): C[T] =
        readArray(in, default, factory.newBuilder, inner)
      def encodeValue(x: C[T], out: JsonWriter): Unit = writeArray(x, out, inner)
    }

  def array[T: ClassTag](inner: JsonValueCodec[T]): JsonValueCodec[Array[T]] = new JsonValueCodec[Array[T]] {
    def nullValue: Array[T] = Array.empty[T]
    def decodeValue(in: JsonReader, default: Array[T]): Array[T] = readArray(in, default, Array.newBuilder[T], inner)
    def encodeValue(x: Array[T], out: JsonWriter): Unit = {
      out.writeArrayStart()
      var i = 0
      while (i < x.length) {
        inner.encodeValue(x(i), out)
        i += 1
      }
      out.writeArrayEnd()
    }
  }

  /** Keys go through `keyToString` / `stringToKey`, values through their codec — the same two functions the schema side
    * (`Schema.schemaForMap(keyToString)`) documents.
    */
  def map[K, V](keyToString: K => String, stringToKey: String => K, values: JsonValueCodec[V]): JsonValueCodec[Map[K, V]] =
    new JsonValueCodec[Map[K, V]] {
      def nullValue: Map[K, V] = Map.empty
      def decodeValue(in: JsonReader, default: Map[K, V]): Map[K, V] =
        if (in.isNextToken('{')) {
          if (in.isNextToken('}')) Map.empty
          else {
            in.rollbackToken()
            val builder = Map.newBuilder[K, V]
            while ({
              val key = stringToKey(in.readKeyAsString())
              builder += key -> values.decodeValue(in, values.nullValue)
              in.isNextToken(',')
            }) ()
            if (in.isCurrentToken('}')) builder.result() else in.objectEndOrCommaError()
          }
        } else in.readNullOrTokenError(default, '{')
      def encodeValue(x: Map[K, V], out: JsonWriter): Unit = {
        out.writeObjectStart()
        x.foreach { case (k, v) =>
          out.writeKey(keyToString(k))
          values.encodeValue(v, out)
        }
        out.writeObjectEnd()
      }
    }

  /** Untagged: a `Left` is written as the bare left value, a `Right` as the bare right value. Decoding tries the right codec first and
    * falls back to the left one — the convention of tapir core's `Codec.eitherRight`, and the only encoding that agrees with core's
    * `Schema.schemaForEither` (a coproduct with no discriminator).
    *
    * The value is read as raw bytes and re-parsed rather than decoded with `setMark`/`rollbackToMark`, because jsoniter does not allow
    * marks to nest and a generated coproduct codec uses one itself (`requireDiscriminatorFirst(false)`).
    */
  def either[A, B](left: JsonValueCodec[A], right: JsonValueCodec[B]): JsonValueCodec[Either[A, B]] =
    new JsonValueCodec[Either[A, B]] {
      def nullValue: Either[A, B] = null
      def decodeValue(in: JsonReader, default: Either[A, B]): Either[A, B] = {
        val raw = in.readRawValAsBytes()
        try Right(readFromArrayReentrant(raw)(right))
        catch {
          case _: JsonReaderException => Left(readFromArrayReentrant(raw)(left))
        }
      }
      def encodeValue(x: Either[A, B], out: JsonWriter): Unit = x match {
        case Left(a)  => left.encodeValue(a, out)
        case Right(b) => right.encodeValue(b, out)
      }
    }

  /** A bare string per value, chosen by `encode`; decoding uses the reverse mapping, built once. */
  def stringEnum[T](values: List[T], encode: T => String): JsonValueCodec[T] = {
    val encoded: Map[T, String] = values.map(v => v -> encode(v)).toMap
    val decoded: Map[String, T] = encoded.map(_.swap)
    if (decoded.size != encoded.size) {
      val duplicates = encoded.groupMap(_._2)(_._1).collect { case (s, vs) if vs.size > 1 => s"'$s' <- ${vs.mkString(", ")}" }
      throw new IllegalArgumentException(
        s"Enumeration encoding is not injective, several values share a string: ${duplicates.mkString("; ")}"
      )
    }
    new JsonValueCodec[T] {
      def nullValue: T = null.asInstanceOf[T]
      def decodeValue(in: JsonReader, default: T): T = {
        val s = in.readString(null)
        if (s eq null) default
        else decoded.getOrElse(s, in.enumValueError(s))
      }
      def encodeValue(x: T, out: JsonWriter): Unit = out.writeVal(encoded(x))
    }
  }

  // -- shared token handling, mirroring JsonCodecMaker's generated code for collections --------------------------

  private def readArray[T, C](
      in: JsonReader,
      default: C,
      builder: scala.collection.mutable.Builder[T, C],
      inner: JsonValueCodec[T]
  ): C =
    if (in.isNextToken('[')) {
      if (in.isNextToken(']')) builder.result()
      else {
        in.rollbackToken()
        while ({
          builder += inner.decodeValue(in, inner.nullValue)
          in.isNextToken(',')
        }) ()
        if (in.isCurrentToken(']')) builder.result() else in.arrayEndOrCommaError()
      }
    } else in.readNullOrTokenError(default, '[')

  private def writeArray[T](xs: Iterable[T], out: JsonWriter, inner: JsonValueCodec[T]): Unit = {
    out.writeArrayStart()
    xs.foreach(inner.encodeValue(_, out))
    out.writeArrayEnd()
  }
}
