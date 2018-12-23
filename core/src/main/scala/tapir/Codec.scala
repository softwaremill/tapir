package tapir

import tapir.DecodeResult._

trait Codec[T, M <: MediaType, R] {
  val rawValueType: RawValueType[R]

  def encodeOptional(t: T): Option[R]
  def decodeOptional(s: Option[R]): DecodeResult[T]
  def isOptional: Boolean
  def schema: Schema
  def mediaType: M

  def map[TT](f: T => TT)(g: TT => T): Codec[TT, M, R] = new MappedCodec[T, TT, M, R](this, f, g)
}

class MappedCodec[T, TT, M <: MediaType, R](val nested: Codec[T, M, R], f: T => TT, g: TT => T) extends Codec[TT, M, R] {
  override val rawValueType: RawValueType[R] = nested.rawValueType

  override def encodeOptional(t: TT): Option[R] = nested.encodeOptional(g(t))
  override def decodeOptional(s: Option[R]): DecodeResult[TT] = nested.decodeOptional(s).map(f)
  override def isOptional: Boolean = nested.isOptional
  override def schema: Schema = nested.schema
  override def mediaType: M = nested.mediaType
}

trait RequiredCodec[T, M <: MediaType, R] extends Codec[T, M, R] {
  def encode(t: T): R
  def decode(s: R): DecodeResult[T]

  override def encodeOptional(t: T): Option[R] = Some(encode(t))
  override def decodeOptional(s: Option[R]): DecodeResult[T] = s match {
    case None     => DecodeResult.Missing
    case Some(ss) => decode(ss)
  }

  def isOptional: Boolean = false
}

object Codec {
  type PlainCodec[T] = Codec[T, MediaType.TextPlain, String]
  type RequiredPlainCodec[T] = RequiredCodec[T, MediaType.TextPlain, String]

  type JsonCodec[T] = Codec[T, MediaType.Json, String]
  type RequiredJsonCodec[T] = RequiredCodec[T, MediaType.Json, String]

  implicit val stringPlainCodec: RequiredPlainCodec[String] = plainCodec[String](identity, Schema.SString)
  implicit val shortPlainCodec: RequiredPlainCodec[Short] = plainCodec[Short](_.toShort, Schema.SInteger)
  implicit val intPlainCodec: RequiredPlainCodec[Int] = plainCodec[Int](_.toInt, Schema.SInteger)
  implicit val longPlainCodec: RequiredPlainCodec[Long] = plainCodec[Long](_.toLong, Schema.SInteger)
  implicit val floatPlainCodec: RequiredPlainCodec[Float] = plainCodec[Float](_.toFloat, Schema.SNumber)
  implicit val doublePlainCodec: RequiredPlainCodec[Double] = plainCodec[Double](_.toDouble, Schema.SNumber)
  implicit val booleanPlainCodec: RequiredPlainCodec[Boolean] = plainCodec[Boolean](_.toBoolean, Schema.SBoolean)

  private def plainCodec[T](parse: String => T, _schema: Schema): RequiredPlainCodec[T] =
    new RequiredPlainCodec[T] {
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

  implicit def optionalCodec[T, M <: MediaType, R](implicit tm: RequiredCodec[T, M, R]): Codec[Option[T], M, R] =
    new Codec[Option[T], M, R] {
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
  implicit val byteArrayCodec: RequiredCodec[Array[Byte], MediaType.OctetStream, Array[Byte]] =
    new RequiredCodec[Array[Byte], MediaType.OctetStream, Array[Byte]] {
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
