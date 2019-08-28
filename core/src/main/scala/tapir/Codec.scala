package tapir

import java.io.{File, InputStream}
import java.math.{BigDecimal => JBigDecimal}
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import java.util.UUID

import tapir.DecodeResult._
import tapir.generic.{FormCodecDerivation, MultipartCodecDerivation}
import tapir.internal.UrlencodedData
import tapir.model.Part

import scala.annotation.implicitNotFound
import scala.util.{Failure, Success, Try}

/**
  * A pair of functions, one to encode a value of type `T` to a raw value of type `R`,
  * and another one to decode.
  *
  * Also contains meta-data on the `schema` of the value, the media type and the raw value type.
  *
  * @tparam T Type of the values which can be encoded / to which raw values can be decoded.
  * @tparam M The media type of encoded values.
  * @tparam R Type of the raw value to which values are encoded.
  */
@implicitNotFound(msg = """Cannot find a codec for type: ${T} and media type: ${M}.
Did you define a codec for: ${T}?
Did you import the codecs for: ${M}?
Is there an implicit schema for: ${T}, and all of its components?
(codecs are looked up as implicit values of type Codec[${T}, ${M}, _];
schemas are looked up as implicit values of type SchemaFor[${T}])
""")
trait Codec[T, M <: MediaType, R] extends Decode[R, T] { outer =>
  def encode(t: T): R
  def rawDecode(s: R): DecodeResult[T]
  def meta: CodecMeta[T, M, R]

  def mapDecode[TT](f: T => DecodeResult[TT])(g: TT => T): Codec[TT, M, R] =
    new Codec[TT, M, R] {
      override def encode(t: TT): R = outer.encode(g(t))
      override def rawDecode(s: R): DecodeResult[TT] = outer.rawDecode(s).flatMap(f)
      override val meta: CodecMeta[TT, M, R] = outer.meta.copy(validator = outer.validator.contramap(g))
    }

  def map[TT](f: T => TT)(g: TT => T): Codec[TT, M, R] = mapDecode[TT](f.andThen(Value.apply))(g)

  private def withMeta[M2 <: MediaType](meta2: CodecMeta[T, M2, R]): Codec[T, M2, R] = new Codec[T, M2, R] {
    override def encode(t: T): R = outer.encode(t)
    override def rawDecode(s: R): DecodeResult[T] = outer.rawDecode(s)
    override val meta: CodecMeta[T, M2, R] = meta2
  }

  def mediaType[M2 <: MediaType](m2: M2): Codec[T, M2, R] = withMeta[M2](meta.copy[T, M2, R](mediaType = m2))
  def schema(s2: Schema): Codec[T, M, R] = withMeta(meta.copy(schema = s2))

  private[tapir] def validator: Validator[T] = meta.validator

  def validate(v: Validator[T]): Codec[T, M, R] = new Codec[T, M, R] {
    override def encode(t: T): R = outer.encode(t)
    override def rawDecode(s: R): DecodeResult[T] = outer.rawDecode(s)
    override def meta: CodecMeta[T, M, R] = outer.meta.copy(validator = v)
  }
}

object Codec extends MultipartCodecDerivation with FormCodecDerivation {
  type PlainCodec[T] = Codec[T, MediaType.TextPlain, String]
  type JsonCodec[T] = Codec[T, MediaType.Json, String]

  implicit val stringPlainCodecUtf8: PlainCodec[String] = stringCodec(StandardCharsets.UTF_8)
  implicit val bytePlainCodec: PlainCodec[Byte] = plainCodec[Byte](_.toByte, Schema.SInteger)
  implicit val shortPlainCodec: PlainCodec[Short] = plainCodec[Short](_.toShort, Schema.SInteger)
  implicit val intPlainCodec: PlainCodec[Int] = plainCodec[Int](_.toInt, Schema.SInteger)
  implicit val longPlainCodec: PlainCodec[Long] = plainCodec[Long](_.toLong, Schema.SInteger)
  implicit val floatPlainCodec: PlainCodec[Float] = plainCodec[Float](_.toFloat, Schema.SNumber)
  implicit val doublePlainCodec: PlainCodec[Double] = plainCodec[Double](_.toDouble, Schema.SNumber)
  implicit val booleanPlainCodec: PlainCodec[Boolean] = plainCodec[Boolean](_.toBoolean, Schema.SBoolean)
  implicit val uuidPlainCodec: PlainCodec[UUID] = plainCodec[UUID](UUID.fromString, Schema.SString)
  implicit val bigDecimalPlainCodec: PlainCodec[BigDecimal] = plainCodec[BigDecimal](BigDecimal(_), Schema.SString)
  implicit val javaBigDecimalPlainCodec: PlainCodec[JBigDecimal] = plainCodec[JBigDecimal](new JBigDecimal(_), Schema.SString)

  implicit val textHtmlCodecUtf8: Codec[String, MediaType.TextHtml, String] = stringPlainCodecUtf8.mediaType(MediaType.TextHtml())

  def stringCodec(charset: Charset): PlainCodec[String] = plainCodec(identity, Schema.SString, charset)

  private def plainCodec[T](parse: String => T, _schema: Schema, charset: Charset = StandardCharsets.UTF_8): PlainCodec[T] =
    new PlainCodec[T] {
      override def encode(t: T): String = t.toString
      override def rawDecode(s: String): DecodeResult[T] =
        try Value(parse(s))
        catch {
          case e: Exception => Error(s, e)
        }
      override val meta: CodecMeta[T, MediaType.TextPlain, String] =
        CodecMeta(_schema, MediaType.TextPlain(charset), StringValueType(charset))
    }

  implicit val byteArrayCodec: Codec[Array[Byte], MediaType.OctetStream, Array[Byte]] = binaryCodec(ByteArrayValueType)
  implicit val byteBufferCodec: Codec[ByteBuffer, MediaType.OctetStream, ByteBuffer] = binaryCodec(ByteBufferValueType)
  implicit val inputStreamCodec: Codec[InputStream, MediaType.OctetStream, InputStream] = binaryCodec(InputStreamValueType)
  implicit val fileCodec: Codec[File, MediaType.OctetStream, File] = binaryCodec(FileValueType)
  implicit val pathCodec: Codec[Path, MediaType.OctetStream, File] = binaryCodec(FileValueType).map(_.toPath)(_.toFile)

  def binaryCodec[T](_rawValueType: RawValueType[T]): Codec[T, MediaType.OctetStream, T] = new Codec[T, MediaType.OctetStream, T] {
    override def encode(b: T): T = b
    override def rawDecode(b: T): DecodeResult[T] = Value(b)
    override val meta: CodecMeta[T, MediaType.OctetStream, T] = CodecMeta(Schema.SBinary, MediaType.OctetStream(), _rawValueType)
  }

  implicit val formSeqCodecUtf8: Codec[Seq[(String, String)], MediaType.XWwwFormUrlencoded, String] = formSeqCodec(StandardCharsets.UTF_8)
  implicit val formMapCodecUtf8: Codec[Map[String, String], MediaType.XWwwFormUrlencoded, String] = formMapCodec(StandardCharsets.UTF_8)

  def formSeqCodec(charset: Charset): Codec[Seq[(String, String)], MediaType.XWwwFormUrlencoded, String] =
    stringCodec(charset)
      .map(UrlencodedData.decode(_, charset))(UrlencodedData.encode(_, charset))
      .mediaType(MediaType.XWwwFormUrlencoded())
  def formMapCodec(charset: Charset): Codec[Map[String, String], MediaType.XWwwFormUrlencoded, String] =
    formSeqCodec(charset).map(_.toMap)(_.toSeq)

  implicit val multipartFormSeqCodec: Codec[Seq[AnyPart], MediaType.MultipartFormData, Seq[RawPart]] =
    multipartCodec(Map.empty, defaultCodec = Some(CodecForMany.fromCodec(byteArrayCodec)))

  /**
    * @param partCodecs For each supported part, a codec which encodes the part value into a raw value. A single part
    *                   value might be encoded as multiple (or none) raw values.
    * @param defaultCodec Default codec to use for parts which are not defined in `partCodecs`. `None`, if extra parts
    *                     should be discarded.
    */
  def multipartCodec(
      partCodecs: Map[String, AnyCodecForMany],
      defaultCodec: Option[AnyCodecForMany]
  ): Codec[Seq[AnyPart], MediaType.MultipartFormData, Seq[RawPart]] =
    new Codec[Seq[AnyPart], MediaType.MultipartFormData, Seq[RawPart]] {
      private val mvt = MultipartValueType(partCodecs.mapValues(_.meta).toMap, defaultCodec.map(_.meta))

      private def partCodec(name: String): Option[AnyCodecForMany] = partCodecs.get(name).orElse(defaultCodec)

      override def encode(t: Seq[AnyPart]): Seq[RawPart] = {
        t.flatMap { part =>
          partCodec(part.name).toList.flatMap { codec =>
            // a single value-part might yield multiple raw-parts (e.g. for repeated fields)
            val rawParts: Seq[RawPart] = codec.asInstanceOf[CodecForMany[Any, _, _]].encode(part.body).map { b =>
              part.copy(body = b)
            }

            rawParts
          }
        }
      }
      override def rawDecode(r: Seq[RawPart]): DecodeResult[Seq[AnyPart]] = {
        val rawPartsByName = r.groupBy(_.name)

        // we need to decode all parts for which there's a codec defined (even if that part is missing a value -
        // it might still decode to e.g. None), and if there's a default codec also the extra parts
        val partNamesToDecode = partCodecs.keys.toSet ++ (if (defaultCodec.isDefined) rawPartsByName.keys.toSet else Set.empty)

        // there might be multiple raw-parts for each name, yielding a single value-part
        val anyParts: List[DecodeResult[AnyPart]] = partNamesToDecode.map { name =>
          val codec = partCodec(name).get
          val rawParts = rawPartsByName.get(name).toList.flatten
          codec.asInstanceOf[CodecForMany[_, _, Any]].rawDecode(rawParts.map(_.body)).map { body =>
            // we know there's at least one part. Using this part to create the value-part
            rawParts.headOption match {
              case Some(rawPart) => rawPart.copy(body = body)
              case None          => Part(name, body)
            }
          }
        }.toList

        DecodeResult.sequence(anyParts)
      }
      override val meta: CodecMeta[Seq[AnyPart], MediaType.MultipartFormData, Seq[RawPart]] =
        CodecMeta(Schema.SBinary, MediaType.MultipartFormData(), mvt)
    }
}

/**
  * A codec which can encode to optional raw values / decode from optional raw values.
  * An optional raw value specifies if the raw value should be included in the output, or not.
  * Depending on the codec, decoding from an optional value might yield [[DecodeResult.Missing]].
  *
  * Should be used for inputs/outputs which allow optional values.
  */
@implicitNotFound(msg = """Cannot find a codec for type: ${T} and media type: ${M}.
Did you define a codec for: ${T}?
Did you import the codecs for: ${M}?
Is there an implicit schema for: ${T}, and all of its components?
(codecs are looked up as implicit values of type Codec[${T}, ${M}, _];
schemas are looked up as implicit values of type SchemaFor[${T}])
""")
trait CodecForOptional[T, M <: MediaType, R] extends Decode[Option[R], T] { outer =>
  def encode(t: T): Option[R]
  def rawDecode(s: Option[R]): DecodeResult[T]
  def meta: CodecMeta[T, M, R]
  private[tapir] def validator: Validator[T] = meta.validator

  def validate(v: Validator[T]): CodecForOptional[T, M, R] = new CodecForOptional[T, M, R] {
    override def encode(t: T): Option[R] = outer.encode(t)
    override def rawDecode(s: Option[R]): DecodeResult[T] = outer.rawDecode(s)
    override def meta: CodecMeta[T, M, R] = outer.meta.copy(validator = v)
  }

  def mapDecode[TT](f: T => DecodeResult[TT])(g: TT => T): CodecForOptional[TT, M, R] =
    new CodecForOptional[TT, M, R] {
      override def encode(t: TT): Option[R] = outer.encode(g(t))
      override def rawDecode(s: Option[R]): DecodeResult[TT] = outer.rawDecode(s).flatMap(f)
      override val meta: CodecMeta[TT, M, R] = outer.meta.copy(validator = outer.meta.validator.contramap(g))
    }

  def map[TT](f: T => TT)(g: TT => T): CodecForOptional[TT, M, R] = mapDecode[TT](f.andThen(Value.apply))(g)
}

object CodecForOptional {
  type PlainCodecForOptional[T] = CodecForOptional[T, MediaType.TextPlain, String]

  implicit def fromCodec[T, M <: MediaType, R](implicit c: Codec[T, M, R]): CodecForOptional[T, M, R] =
    new CodecForOptional[T, M, R] {
      override def encode(t: T): Option[R] = Some(c.encode(t))
      override def rawDecode(s: Option[R]): DecodeResult[T] = s match {
        case None    => DecodeResult.Missing
        case Some(h) => c.rawDecode(h)
      }
      override val meta: CodecMeta[T, M, R] = c.meta
    }

  implicit def forOption[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): CodecForOptional[Option[T], M, R] =
    new CodecForOptional[Option[T], M, R] {
      override def encode(t: Option[T]): Option[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Option[R]): DecodeResult[Option[T]] = s match {
        case None     => DecodeResult.Value(None)
        case Some(ss) => tm.rawDecode(ss).map(Some(_))
      }
      override val meta: CodecMeta[Option[T], M, R] = tm.meta.copy(isOptional = true, validator = tm.validator.asOptionElement)
    }
}

/**
  * A codec which can encode to multiple (0..n) raw values / decode from multiple raw values.
  * An multiple raw value specifies that the raw values should be included in the output multiple times.
  * Depending on the codec, decoding from a multiple value might yield [[DecodeResult.Missing]] or [[DecodeResult.Multiple]].
  *
  * Should be used for inputs/outputs which allow multiple values.
  */
@implicitNotFound(msg = """Cannot find a codec for type: ${T} and media type: ${M}.
Did you specify the target type of the input/output?
Did you define a codec for: ${T}?
Did you import the codecs for: ${M}?
Is there an implicit schema for: ${T}, and all of its components?
(codecs are looked up as implicit values of type Codec[${T}, ${M}, _];
schemas are looked up as implicit values of type SchemaFor[${T}])
""")
trait CodecForMany[T, M <: MediaType, R] extends Decode[Seq[R], T] { outer =>
  def encode(t: T): Seq[R]
  def rawDecode(s: Seq[R]): DecodeResult[T]
  def meta: CodecMeta[T, M, R]
  private[tapir] def validator: Validator[T] = meta.validator

  def validate(v: Validator[T]): CodecForMany[T, M, R] = new CodecForMany[T, M, R] {
    override def encode(t: T): Seq[R] = outer.encode(t)
    override def rawDecode(s: Seq[R]): DecodeResult[T] = outer.rawDecode(s)
    override def meta: CodecMeta[T, M, R] = outer.meta.copy(validator = v)
  }

  def mapDecode[TT](f: T => DecodeResult[TT])(g: TT => T): CodecForMany[TT, M, R] =
    new CodecForMany[TT, M, R] {
      override def encode(t: TT): Seq[R] = outer.encode(g(t))
      override def rawDecode(s: Seq[R]): DecodeResult[TT] = outer.rawDecode(s).flatMap(f)
      override val meta: CodecMeta[TT, M, R] = outer.meta.copy(validator = outer.meta.validator.contramap(g))
    }

  def map[TT](f: T => TT)(g: TT => T): CodecForMany[TT, M, R] = mapDecode[TT](f.andThen(Value.apply))(g)
}

object CodecForMany {
  type PlainCodecForMany[T] = CodecForMany[T, MediaType.TextPlain, String]

  implicit def fromCodec[T, M <: MediaType, R](implicit c: Codec[T, M, R]): CodecForMany[T, M, R] = new CodecForMany[T, M, R] {
    override def encode(t: T): Seq[R] = List(c.encode(t))
    override def rawDecode(s: Seq[R]): DecodeResult[T] = s match {
      case Nil     => DecodeResult.Missing
      case List(h) => c.rawDecode(h)
      case l       => DecodeResult.Multiple(l)
    }
    override val meta: CodecMeta[T, M, R] = c.meta
  }

  implicit def forOption[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): CodecForMany[Option[T], M, R] =
    new CodecForMany[Option[T], M, R] {
      override def encode(t: Option[T]): Seq[R] = t.map(v => tm.encode(v)).toList
      override def rawDecode(s: Seq[R]): DecodeResult[Option[T]] = s match {
        case Nil     => DecodeResult.Value(None)
        case List(h) => tm.rawDecode(h).map(Some(_))
        case l       => DecodeResult.Multiple(l)
      }
      override val meta: CodecMeta[Option[T], M, R] = tm.meta.copy(isOptional = true, validator = tm.meta.validator.asOptionElement)
    }

  // collections

  implicit def forSeq[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): CodecForMany[Seq[T], M, R] =
    new CodecForMany[Seq[T], M, R] {
      override def encode(t: Seq[T]): Seq[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Seq[R]): DecodeResult[Seq[T]] = DecodeResult.sequence(s.map(tm.rawDecode))
      override val meta: CodecMeta[Seq[T], M, R] =
        tm.meta.copy(isOptional = true, schema = Schema.SArray(tm.meta.schema), validator = tm.validator.asIterableElements)
    }

  implicit def forList[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): CodecForMany[List[T], M, R] =
    new CodecForMany[List[T], M, R] {
      override def encode(t: List[T]): Seq[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Seq[R]): DecodeResult[List[T]] = DecodeResult.sequence(s.map(tm.rawDecode)).map(_.toList)
      override val meta: CodecMeta[List[T], M, R] =
        tm.meta.copy(isOptional = true, schema = Schema.SArray(tm.meta.schema), validator = tm.validator.asIterableElements)
    }

  implicit def forVector[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): CodecForMany[Vector[T], M, R] =
    new CodecForMany[Vector[T], M, R] {
      override def encode(t: Vector[T]): Seq[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Seq[R]): DecodeResult[Vector[T]] = DecodeResult.sequence(s.map(tm.rawDecode)).map(_.toVector)
      override val meta: CodecMeta[Vector[T], M, R] =
        tm.meta.copy(isOptional = true, schema = Schema.SArray(tm.meta.schema), validator = tm.validator.asIterableElements)
    }

  implicit def forSet[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): CodecForMany[Set[T], M, R] =
    new CodecForMany[Set[T], M, R] {
      override def encode(t: Set[T]): Seq[R] = t.map(v => tm.encode(v)).toSeq
      override def rawDecode(s: Seq[R]): DecodeResult[Set[T]] = DecodeResult.sequence(s.map(tm.rawDecode)).map(_.toSet)
      override val meta: CodecMeta[Set[T], M, R] =
        tm.meta.copy(isOptional = true, schema = Schema.SArray(tm.meta.schema), validator = tm.validator.asIterableElements)
    }
}

case class CodecMeta[T, M <: MediaType, R] private (
    isOptional: Boolean,
    schema: Schema,
    mediaType: M,
    rawValueType: RawValueType[R],
    validator: Validator[T]
)
object CodecMeta {
  def apply[T, M <: MediaType, R](
      schema: Schema,
      mediaType: M,
      rawValueType: RawValueType[R],
      validator: Validator[T] = Validator.pass[T]
  ): CodecMeta[T, M, R] =
    CodecMeta(isOptional = false, schema, mediaType, rawValueType, validator)
}

sealed trait RawValueType[R]
case class StringValueType(charset: Charset) extends RawValueType[String]
case object ByteArrayValueType extends RawValueType[Array[Byte]]
case object ByteBufferValueType extends RawValueType[ByteBuffer]
case object InputStreamValueType extends RawValueType[InputStream]
case object FileValueType extends RawValueType[File]
case class MultipartValueType(partCodecMetas: Map[String, AnyCodecMeta], defaultCodecMeta: Option[AnyCodecMeta])
    extends RawValueType[Seq[RawPart]] {
  def partCodecMeta(name: String): Option[AnyCodecMeta] = partCodecMetas.get(name).orElse(defaultCodecMeta)
}

trait Decode[F, T] {
  def rawDecode(s: F): DecodeResult[T]

  private[tapir] def validator: Validator[T]

  /**
    * - calls `rawDecode`
    * - catches any exceptions that might occur, converting them to decode failures
    * - validates the result
    */
  def decode(f: F): DecodeResult[T] = validate(tryRawDecode(f))

  private def tryRawDecode(f: F): DecodeResult[T] = {
    Try(rawDecode(f)) match {
      case Success(r) => r
      case Failure(e) => DecodeResult.Error(f.toString, e)
    }
  }

  private def validate(r: DecodeResult[T]): DecodeResult[T] = {
    r match {
      case DecodeResult.Value(v) =>
        val validationErrors = validator.validate(v)
        if (validationErrors.isEmpty) {
          DecodeResult.Value(v)
        } else {
          DecodeResult.InvalidValue(validationErrors)
        }
      case r => r
    }
  }
}
