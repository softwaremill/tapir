package tapir

import tapir.DecodeResult._

/**
  * A codec which can encode/decode both optional and non-optional values.
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
  * A pair of functions, one to encode a non-optional value to a raw value, and another one to decode.
  * Also contains the schema of the value, as well as the meta-data on the media and the raw value type.
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

  implicit val stringPlainCodec: PlainCodec[String] = plainCodec[String](identity, Schema.SString)
  implicit val shortPlainCodec: PlainCodec[Short] = plainCodec[Short](_.toShort, Schema.SInteger)
  implicit val intPlainCodec: PlainCodec[Int] = plainCodec[Int](_.toInt, Schema.SInteger)
  implicit val longPlainCodec: PlainCodec[Long] = plainCodec[Long](_.toLong, Schema.SInteger)
  implicit val floatPlainCodec: PlainCodec[Float] = plainCodec[Float](_.toFloat, Schema.SNumber)
  implicit val doublePlainCodec: PlainCodec[Double] = plainCodec[Double](_.toDouble, Schema.SNumber)
  implicit val booleanPlainCodec: PlainCodec[Boolean] = plainCodec[Boolean](_.toBoolean, Schema.SBoolean)

  private def plainCodec[T](parse: String => T, _schema: Schema): PlainCodec[T] =
    new PlainCodec[T] {
      override val rawValueType: RawValueType[String] = StringValueType

      override def encode(t: T): String = t.toString
      override def decode(s: String): DecodeResult[T] =
        try Value(parse(s))
        catch {
          case e: Exception => Error(s, e, "Cannot parse")
        }
      override def schema: Schema = _schema
      override def mediaType = MediaType.TextPlain()
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

  // TODO: codec == map functions; others (R, schema, media type) added as implicits?
  implicit val byteArrayCodec: Codec[Array[Byte], MediaType.OctetStream, Array[Byte]] =
    new Codec[Array[Byte], MediaType.OctetStream, Array[Byte]] {
      override val rawValueType: RawValueType[Array[Byte]] = ByteArrayValueType

      override def encode(b: Array[Byte]): Array[Byte] = b
      override def decode(b: Array[Byte]): DecodeResult[Array[Byte]] = Value(b)
      override def schema: Schema = Schema.SBinary()
      override def mediaType = MediaType.OctetStream()
    }
}

// string, byte array, input stream, file, form values (?), stream
sealed trait RawValueType[R] {
  def fold[T](r: R)(fs: String => T, fb: Array[Byte] => T): T
}
case object StringValueType extends RawValueType[String] {
  override def fold[T](r: String)(fs: String => T, fb: Array[Byte] => T): T = fs(r)
}
case object ByteArrayValueType extends RawValueType[Array[Byte]] {
  override def fold[T](r: Array[Byte])(fs: String => T, fb: Array[Byte] => T): T = fb(r)
}
