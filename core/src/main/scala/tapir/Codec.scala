package tapir

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

import tapir.DecodeResult._

/**
  * A codec which can encode/decode both optional and non-optional values of type `T` to raw values of type `R`.
  * Base trait for all codecs.
  */
trait GeneralCodec[T, M <: MediaType, R] { outer =>
  val rawValueType: RawValueType[R]

  def encodeOptional(t: T): Option[R]
  def decodeOptional(s: Option[R]): DecodeResult[T]
  def isOptional: Boolean
  def schema: Schema
  def mediaType: M

  def map[TT](f: T => TT)(g: TT => T): GeneralCodec[TT, M, R] = new GeneralCodec[TT, M, R] {
    override val rawValueType: RawValueType[R] = outer.rawValueType

    override def encodeOptional(t: TT): Option[R] = outer.encodeOptional(g(t))
    override def decodeOptional(s: Option[R]): DecodeResult[TT] = outer.decodeOptional(s).map(f)
    override def isOptional: Boolean = outer.isOptional
    override def schema: Schema = outer.schema
    override def mediaType: M = outer.mediaType
  }
}

/**
  * A pair of functions, one to encode a non-optional value of type `T` to a raw value of type `R`,
  * and another one to decode.
  *
  * Also contains the `schema` of the value, as well as the meta-data on the media and the raw value type.
  *
  * @tparam T Type of the values which can be encoded / to which raw values can be decoded.
  * @tparam M The media type of encoded values.
  * @tparam R Type of the raw value to which values are encoded.
  */
trait Codec[T, M <: MediaType, R] extends GeneralCodec[T, M, R] {
  def encode(t: T): R
  def decode(s: R): DecodeResult[T]

  override def encodeOptional(t: T): Option[R] = Some(encode(t))
  override def decodeOptional(s: Option[R]): DecodeResult[T] = s match {
    case None     => DecodeResult.Missing
    case Some(ss) => decode(ss)
  }

  def isOptional: Boolean = false
}

object GeneralCodec {
  type GeneralPlainCodec[T] = GeneralCodec[T, MediaType.TextPlain, String]
  type PlainCodec[T] = Codec[T, MediaType.TextPlain, String]

  type GeneralJsonCodec[T] = GeneralCodec[T, MediaType.Json, String]
  type JsonCodec[T] = Codec[T, MediaType.Json, String]

  implicit val stringPlainCodecUtf8: PlainCodec[String] = stringCodec(StandardCharsets.UTF_8)
  implicit val shortPlainCodec: PlainCodec[Short] = plainCodec[Short](_.toShort, Schema.SInteger)
  implicit val intPlainCodec: PlainCodec[Int] = plainCodec[Int](_.toInt, Schema.SInteger)
  implicit val longPlainCodec: PlainCodec[Long] = plainCodec[Long](_.toLong, Schema.SInteger)
  implicit val floatPlainCodec: PlainCodec[Float] = plainCodec[Float](_.toFloat, Schema.SNumber)
  implicit val doublePlainCodec: PlainCodec[Double] = plainCodec[Double](_.toDouble, Schema.SNumber)
  implicit val booleanPlainCodec: PlainCodec[Boolean] = plainCodec[Boolean](_.toBoolean, Schema.SBoolean)

  def stringCodec(charset: Charset): PlainCodec[String] = plainCodec(identity, Schema.SString, charset)

  private def plainCodec[T](parse: String => T, _schema: Schema, charset: Charset = StandardCharsets.UTF_8): PlainCodec[T] =
    new PlainCodec[T] {
      override val rawValueType: RawValueType[String] = StringValueType(charset)

      override def encode(t: T): String = t.toString
      override def decode(s: String): DecodeResult[T] =
        try Value(parse(s))
        catch {
          case e: Exception => Error(s, e, "Cannot parse")
        }
      override def schema: Schema = _schema
      override def mediaType = MediaType.TextPlain(charset)
    }

  implicit def optionalCodec[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): GeneralCodec[Option[T], M, R] =
    new GeneralCodec[Option[T], M, R] {
      override val rawValueType: RawValueType[R] = tm.rawValueType

      override def encodeOptional(t: Option[T]): Option[R] = t.map(v => tm.encode(v))
      override def decodeOptional(s: Option[R]): DecodeResult[Option[T]] = s match {
        case None => DecodeResult.Value(None)
        case Some(ss) =>
          tm.decode(ss) match {
            case DecodeResult.Value(v)  => DecodeResult.Value(Some(v))
            case DecodeResult.Missing   => DecodeResult.Missing
            case de: DecodeResult.Error => de
          }
      }
      override def isOptional: Boolean = true
      override def schema: Schema = tm.schema
      override def mediaType: M = tm.mediaType
    }

  implicit val byteArrayCodec: Codec[Array[Byte], MediaType.OctetStream, Array[Byte]] = binaryCodec(ByteArrayValueType)
  implicit val byteBufferCodec: Codec[ByteBuffer, MediaType.OctetStream, ByteBuffer] = binaryCodec(ByteBufferValueType)
  implicit val inputStreamCodec: Codec[InputStream, MediaType.OctetStream, InputStream] = binaryCodec(InputStreamValueType)

  def binaryCodec[T](_rawValueType: RawValueType[T]): Codec[T, MediaType.OctetStream, T] = new Codec[T, MediaType.OctetStream, T] {
    override val rawValueType: RawValueType[T] = _rawValueType

    override def encode(b: T): T = b
    override def decode(b: T): DecodeResult[T] = Value(b)
    override def schema: Schema = Schema.SBinary()
    override def mediaType = MediaType.OctetStream()
  }
}

// string, byte array, input stream, file, form values (?), stream
sealed trait RawValueType[R]
case class StringValueType(charset: Charset) extends RawValueType[String]
case object ByteArrayValueType extends RawValueType[Array[Byte]]
case object ByteBufferValueType extends RawValueType[ByteBuffer]
case object InputStreamValueType extends RawValueType[InputStream]
