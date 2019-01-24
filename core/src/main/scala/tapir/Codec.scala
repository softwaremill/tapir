package tapir

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path

import tapir.DecodeResult._
import tapir.generic.FormCodecDerivation
import tapir.internal.UrlencodedData

/**
  * A codec which can encode/decode both optional and non-optional values of type `T` to raw values of type `R`.
  * Base trait for all codecs.
  */
trait GeneralCodec[T, M <: MediaType, R] { outer =>
  val rawValueType: RawValueType[R]

  def encodeMany(t: T): List[R]
  def decodeMany(s: List[R]): DecodeResult[T]
  def meta: CodecMeta[M]

  def map[TT](f: T => TT)(g: TT => T): GeneralCodec[TT, M, R] = new GeneralCodec[TT, M, R] {
    override val rawValueType: RawValueType[R] = outer.rawValueType
    override def encodeMany(t: TT): List[R] = outer.encodeMany(g(t))
    override def decodeMany(s: List[R]): DecodeResult[TT] = outer.decodeMany(s).map(f)
    override def meta: CodecMeta[M] = outer.meta
  }

  private def withMeta[M2 <: MediaType](meta2: CodecMeta[M2]): GeneralCodec[T, M2, R] = new GeneralCodec[T, M2, R] {
    override val rawValueType: RawValueType[R] = outer.rawValueType
    override def encodeMany(t: T): List[R] = outer.encodeMany(t)
    override def decodeMany(s: List[R]): DecodeResult[T] = outer.decodeMany(s)
    override def meta: CodecMeta[M2] = meta2
  }

  def mediaType[M2 <: MediaType](m2: M2): GeneralCodec[T, M2, R] = withMeta(meta.copy[M2](mediaType = m2))
  def schema(s2: Schema): GeneralCodec[T, M, R] = withMeta(meta.copy(schema = s2))
}

/**
  * A pair of functions, one to encode a non-optional value of type `T` to a raw value of type `R`,
  * and another one to decode.
  *
  * Also contains meta-data on the `schema` of the value, the media type and the raw value type.
  *
  * @tparam T Type of the values which can be encoded / to which raw values can be decoded.
  * @tparam M The media type of encoded values.
  * @tparam R Type of the raw value to which values are encoded.
  */
trait Codec[T, M <: MediaType, R] extends GeneralCodec[T, M, R] { outer =>
  def encode(t: T): R
  def decode(s: R): DecodeResult[T]

  override def encodeMany(t: T): List[R] = List(encode(t))
  override def decodeMany(s: List[R]): DecodeResult[T] = s match {
    case Nil     => DecodeResult.Missing
    case List(h) => decode(h)
    case l       => DecodeResult.Multiple(l)
  }

  def mapDecode[TT](f: T => DecodeResult[TT])(g: TT => T): Codec[TT, M, R] =
    new Codec[TT, M, R] {
      override def encode(t: TT): R = outer.encode(g(t))
      override def decode(s: R): DecodeResult[TT] = outer.decode(s).flatMap(f)
      override val rawValueType: RawValueType[R] = outer.rawValueType
      override def meta: CodecMeta[M] = outer.meta
    }

  override def map[TT](f: T => TT)(g: TT => T): Codec[TT, M, R] = mapDecode[TT](f.andThen(Value.apply))(g)

  private def withMeta[M2 <: MediaType](meta2: CodecMeta[M2]): Codec[T, M2, R] = new Codec[T, M2, R] {
    override def encode(t: T): R = outer.encode(t)
    override def decode(s: R): DecodeResult[T] = outer.decode(s)
    override val rawValueType: RawValueType[R] = outer.rawValueType
    override def meta: CodecMeta[M2] = meta2
  }

  override def mediaType[M2 <: MediaType](m2: M2): Codec[T, M2, R] = withMeta[M2](meta.copy[M2](mediaType = m2))
  override def schema(s2: Schema): Codec[T, M, R] = withMeta(meta.copy(schema = s2))
}

object GeneralCodec extends FormCodecDerivation {
  type GeneralPlainCodec[T] = GeneralCodec[T, MediaType.TextPlain, String]
  type PlainCodec[T] = Codec[T, MediaType.TextPlain, String]
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
      override def meta: CodecMeta[MediaType.TextPlain] = CodecMeta(_schema, MediaType.TextPlain(charset))
    }

  implicit def optionalCodec[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): GeneralCodec[Option[T], M, R] =
    new GeneralCodec[Option[T], M, R] {
      override val rawValueType: RawValueType[R] = tm.rawValueType

      override def encodeMany(t: Option[T]): List[R] = t.map(v => tm.encode(v)).toList
      override def decodeMany(s: List[R]): DecodeResult[Option[T]] = s match {
        case Nil => DecodeResult.Value(None)
        case List(ss) =>
          tm.decode(ss) match {
            case DecodeResult.Value(v) => DecodeResult.Value(Some(v))
            case df: DecodeFailure     => df
          }
      }
      override def meta: CodecMeta[M] = tm.meta.copy(isOptional = true)
    }

  implicit def listCodec[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): GeneralCodec[List[T], M, R] =
    new GeneralCodec[List[T], M, R] {
      override val rawValueType: RawValueType[R] = tm.rawValueType

      override def encodeMany(t: List[T]): List[R] = t.map(v => tm.encode(v))
      override def decodeMany(s: List[R]): DecodeResult[List[T]] = DecodeResult.sequence(s.map(tm.decode))
      override def meta: CodecMeta[M] = tm.meta.copy(isOptional = true, schema = Schema.SArray(tm.meta.schema))
    }

  implicit val byteArrayCodec: Codec[Array[Byte], MediaType.OctetStream, Array[Byte]] = binaryCodec(ByteArrayValueType)
  implicit val byteBufferCodec: Codec[ByteBuffer, MediaType.OctetStream, ByteBuffer] = binaryCodec(ByteBufferValueType)
  implicit val inputStreamCodec: Codec[InputStream, MediaType.OctetStream, InputStream] = binaryCodec(InputStreamValueType)
  implicit val fileCodec: Codec[File, MediaType.OctetStream, File] = binaryCodec(FileValueType)
  implicit val pathCodec: Codec[Path, MediaType.OctetStream, File] = binaryCodec(FileValueType).map(_.toPath)(_.toFile)

  def binaryCodec[T](_rawValueType: RawValueType[T]): Codec[T, MediaType.OctetStream, T] = new Codec[T, MediaType.OctetStream, T] {
    override val rawValueType: RawValueType[T] = _rawValueType

    override def encode(b: T): T = b
    override def decode(b: T): DecodeResult[T] = Value(b)
    override def meta: CodecMeta[MediaType.OctetStream] = CodecMeta(Schema.SBinary, MediaType.OctetStream())
  }

  implicit val formSeqCodecUtf8: Codec[Seq[(String, String)], MediaType.XWwwFormUrlencoded, String] = formSeqCodec(StandardCharsets.UTF_8)
  implicit val formMapCodecUtf8: Codec[Map[String, String], MediaType.XWwwFormUrlencoded, String] = formMapCodec(StandardCharsets.UTF_8)

  def formSeqCodec(charset: Charset): Codec[Seq[(String, String)], MediaType.XWwwFormUrlencoded, String] =
    stringCodec(charset).map(UrlencodedData.decode(_, charset))(UrlencodedData.encode(_, charset)).mediaType(MediaType.XWwwFormUrlencoded())
  def formMapCodec(charset: Charset): Codec[Map[String, String], MediaType.XWwwFormUrlencoded, String] =
    formSeqCodec(charset).map(_.toMap)(_.toSeq)
}

case class CodecMeta[M <: MediaType] private (isOptional: Boolean, schema: Schema, mediaType: M)
object CodecMeta {
  def apply[M <: MediaType](schema: Schema, mediaType: M): CodecMeta[M] = CodecMeta(isOptional = false, schema, mediaType)
}

// string, byte array, input stream, file, form values (?), stream
sealed trait RawValueType[R]
case class StringValueType(charset: Charset) extends RawValueType[String]
case object ByteArrayValueType extends RawValueType[Array[Byte]]
case object ByteBufferValueType extends RawValueType[ByteBuffer]
case object InputStreamValueType extends RawValueType[InputStream]
case object FileValueType extends RawValueType[File]
