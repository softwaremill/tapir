package sttp.tapir

import java.io.{File, InputStream}
import java.math.{BigDecimal => JBigDecimal}
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import java.text.DateFormat
import java.time.format.{DateTimeFormatter, DateTimeParseException}

import scala.concurrent.duration.{Duration => SDuration}
import java.time._
import java.time.temporal.TemporalUnit
import java.util.{Date, UUID}

import sttp.model.{Cookie, CookieValueWithMeta, CookieWithMeta, Part, Uri}
import sttp.model.Uri._
import sttp.tapir.DecodeResult._
import sttp.tapir.generic.internal.{FormCodecDerivation, MultipartCodecDerivation}
import sttp.tapir.internal._

import scala.annotation.implicitNotFound
import scala.util.{Failure, Success, Try}

/**
  * A pair of functions: one to encode a value of type `T` to a raw value of type `R` formatted as `CF`,
  * and another one to decode.
  *
  * Also contains meta-data on the `schema` of the value, the codec format and the raw value type.
  *
  * @tparam T Type of the values which can be encoded / to which raw values can be decoded.
  * @tparam CF The format of encoded values. Corresponds to a media type.
  * @tparam R Type of the raw value to which values are encoded.
  */
@implicitNotFound(msg = """Cannot find a codec for type: ${T}, formatted as: ${CF}.
Did you define a codec for: ${T}?
Did you import the codecs for: ${CF}?
Is there an implicit schema for: ${T}, and all of its components?
(codecs are looked up as implicit values of type Codec[${T}, ${CF}, _];
schemas are looked up as implicit values of type Schema[${T}])
""")
trait Codec[T, CF <: CodecFormat, R] extends Decode[T, R] { outer =>
  def encode(t: T): R
  def rawDecode(s: R): DecodeResult[T]
  def meta: CodecMeta[T, CF, R]

  def mapDecode[TT](f: T => DecodeResult[TT])(g: TT => T): Codec[TT, CF, R] =
    new Codec[TT, CF, R] {
      override def encode(t: TT): R = outer.encode(g(t))
      override def rawDecode(s: R): DecodeResult[TT] = outer.rawDecode(s).flatMap(f)
      override val meta: CodecMeta[TT, CF, R] = outer.meta.copy(schema = outer.meta.schema.as[TT], validator = outer.validator.contramap(g))
    }

  def map[TT](f: T => TT)(g: TT => T): Codec[TT, CF, R] = mapDecode[TT](f.andThen(Value.apply))(g)

  private def withMeta[F2 <: CodecFormat](meta2: CodecMeta[T, F2, R]): Codec[T, F2, R] = new Codec[T, F2, R] {
    override def encode(t: T): R = outer.encode(t)
    override def rawDecode(s: R): DecodeResult[T] = outer.rawDecode(s)
    override val meta: CodecMeta[T, F2, R] = meta2
  }

  def codecFormat[F2 <: CodecFormat](f2: F2): Codec[T, F2, R] = withMeta[F2](meta.copy[T, F2, R](format = f2))
  def schema(s2: Schema[T]): Codec[T, CF, R] = withMeta(meta.copy(schema = s2))
  def modifySchema(modify: Schema[T] => Schema[T]): Codec[T, CF, R] = withMeta(meta.copy(schema = modify(meta.schema)))

  private[tapir] def validator: Validator[T] = meta.validator

  def validate(v: Validator[T]): Codec[T, CF, R] = new Codec[T, CF, R] {
    override def encode(t: T): R = outer.encode(t)
    override def rawDecode(s: R): DecodeResult[T] = outer.rawDecode(s)
    override def meta: CodecMeta[T, CF, R] = {
      val withEncodeEnumValidator = v match {
        case v @ Validator.Enum(_, None) => v.encode(encode)
        case _                           => v
      }
      outer.meta.validate(withEncodeEnumValidator)
    }
  }
}

object Codec extends MultipartCodecDerivation with FormCodecDerivation {
  type PlainCodec[T] = Codec[T, CodecFormat.TextPlain, String]
  type JsonCodec[T] = Codec[T, CodecFormat.Json, String]

  implicit val stringPlainCodecUtf8: PlainCodec[String] = stringCodec(StandardCharsets.UTF_8)
  implicit val bytePlainCodec: PlainCodec[Byte] = plainCodec[Byte](_.toByte)
  implicit val shortPlainCodec: PlainCodec[Short] = plainCodec[Short](_.toShort)
  implicit val intPlainCodec: PlainCodec[Int] = plainCodec[Int](_.toInt)
  implicit val longPlainCodec: PlainCodec[Long] = plainCodec[Long](_.toLong)
  implicit val floatPlainCodec: PlainCodec[Float] = plainCodec[Float](_.toFloat)
  implicit val doublePlainCodec: PlainCodec[Double] = plainCodec[Double](_.toDouble)
  implicit val booleanPlainCodec: PlainCodec[Boolean] = plainCodec[Boolean](_.toBoolean)
  implicit val uuidPlainCodec: PlainCodec[UUID] = plainCodec[UUID](UUID.fromString)
  implicit val bigDecimalPlainCodec: PlainCodec[BigDecimal] = plainCodec[BigDecimal](BigDecimal(_))
  implicit val javaBigDecimalPlainCodec: PlainCodec[JBigDecimal] = plainCodec[JBigDecimal](new JBigDecimal(_))

  implicit val uriPlainCodec: PlainCodec[Uri] =
    stringPlainCodecUtf8.mapDecode(raw => Try(uri"$raw").fold(DecodeResult.Error("Invalid URI", _), DecodeResult.Value(_)))(_.toString())

  implicit val textHtmlCodecUtf8: Codec[String, CodecFormat.TextHtml, String] = stringPlainCodecUtf8.codecFormat(CodecFormat.TextHtml())

  def stringCodec(charset: Charset): PlainCodec[String] = plainCodec(identity, charset)

  implicit val localTimePlainCodec: PlainCodec[LocalTime] = plainCodec[LocalTime](LocalTime.parse)
  implicit val localDatePlainCodec: PlainCodec[LocalDate] = plainCodec[LocalDate](LocalDate.parse)
  implicit val offsetDateTimePlainCodec: PlainCodec[OffsetDateTime] = plainCodec[OffsetDateTime](OffsetDateTime.parse)
  implicit val zonedDateTimePlainCodec: PlainCodec[ZonedDateTime] = offsetDateTimePlainCodec.map(_.toZonedDateTime)(_.toOffsetDateTime)
  implicit val instantPlainCodec: PlainCodec[Instant] = zonedDateTimePlainCodec.map(_.toInstant)(_.atZone(ZoneOffset.UTC))
  implicit val datePlainCodec: PlainCodec[Date] = instantPlainCodec.map(Date.from)(_.toInstant)
  implicit val zoneOffsetPlainCodec: PlainCodec[ZoneOffset] = plainCodec[ZoneOffset](ZoneOffset.of)
  implicit val durationPlainCodec: PlainCodec[Duration] = plainCodec[Duration](Duration.parse)
  implicit val offsetTime: PlainCodec[OffsetTime] = plainCodec[OffsetTime](OffsetTime.parse)
  implicit val scalaDurationPlainCodec: PlainCodec[SDuration] = plainCodec[SDuration](SDuration.apply)

  implicit val localDateTimeCodec: PlainCodec[LocalDateTime] =
    new PlainCodec[LocalDateTime] {
      val charset: Charset = StandardCharsets.UTF_8
      override def encode(t: LocalDateTime): String = OffsetDateTime.of(t, ZoneOffset.UTC).toString
      override def rawDecode(s: String): DecodeResult[LocalDateTime] = {
        try {
          try {
            Value(LocalDateTime.parse(s))
          } catch {
            case _: DateTimeParseException => Value(OffsetDateTime.parse(s).toLocalDateTime)
          }
        } catch {
          case e: Exception => Error(s, e)
        }
      }

      override val meta: CodecMeta[LocalDateTime, CodecFormat.TextPlain, String] =
        CodecMeta(implicitly, CodecFormat.TextPlain(charset), StringValueType(charset))
    }

  def plainCodec[T: Schema](parse: String => T, charset: Charset = StandardCharsets.UTF_8): PlainCodec[T] =
    new PlainCodec[T] {
      override def encode(t: T): String = t.toString
      override def rawDecode(s: String): DecodeResult[T] =
        try Value(parse(s))
        catch {
          case e: Exception => Error(s, e)
        }
      override val meta: CodecMeta[T, CodecFormat.TextPlain, String] =
        CodecMeta(implicitly, CodecFormat.TextPlain(charset), StringValueType(charset))
    }

  implicit val byteArrayCodec: Codec[Array[Byte], CodecFormat.OctetStream, Array[Byte]] = binaryCodec(ByteArrayValueType)
  implicit val byteBufferCodec: Codec[ByteBuffer, CodecFormat.OctetStream, ByteBuffer] = binaryCodec(ByteBufferValueType)
  implicit val inputStreamCodec: Codec[InputStream, CodecFormat.OctetStream, InputStream] = binaryCodec(InputStreamValueType)
  implicit val fileCodec: Codec[File, CodecFormat.OctetStream, File] = binaryCodec(FileValueType)
  implicit val pathCodec: Codec[Path, CodecFormat.OctetStream, File] = binaryCodec(FileValueType).map(_.toPath)(_.toFile)

  private def binaryCodec[T: Schema](_rawValueType: RawValueType[T]): Codec[T, CodecFormat.OctetStream, T] =
    new Codec[T, CodecFormat.OctetStream, T] {
      override def encode(b: T): T = b
      override def rawDecode(b: T): DecodeResult[T] = Value(b)
      override val meta: CodecMeta[T, CodecFormat.OctetStream, T] = CodecMeta(implicitly, CodecFormat.OctetStream(), _rawValueType)
    }

  implicit val formSeqCodecUtf8: Codec[Seq[(String, String)], CodecFormat.XWwwFormUrlencoded, String] = formSeqCodec(StandardCharsets.UTF_8)
  implicit val formMapCodecUtf8: Codec[Map[String, String], CodecFormat.XWwwFormUrlencoded, String] = formMapCodec(StandardCharsets.UTF_8)

  def formSeqCodec(charset: Charset): Codec[Seq[(String, String)], CodecFormat.XWwwFormUrlencoded, String] =
    stringCodec(charset)
      .map(UrlencodedData.decode(_, charset))(UrlencodedData.encode(_, charset))
      .codecFormat(CodecFormat.XWwwFormUrlencoded())
  def formMapCodec(charset: Charset): Codec[Map[String, String], CodecFormat.XWwwFormUrlencoded, String] =
    formSeqCodec(charset).map(_.toMap)(_.toSeq)

  implicit val multipartFormSeqCodec: Codec[Seq[AnyPart], CodecFormat.MultipartFormData, Seq[RawPart]] =
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
  ): Codec[Seq[AnyPart], CodecFormat.MultipartFormData, Seq[RawPart]] =
    new Codec[Seq[AnyPart], CodecFormat.MultipartFormData, Seq[RawPart]] {
      private val mvt = MultipartValueType(partCodecs.mapValues(_.meta).toMap, defaultCodec.map(_.meta))

      private def partCodec(name: String): Option[AnyCodecForMany] = partCodecs.get(name).orElse(defaultCodec)

      override def encode(t: Seq[AnyPart]): Seq[RawPart] = {
        t.flatMap { part =>
          partCodec(part.name).toList.flatMap { codec =>
            // a single value-part might yield multiple raw-parts (e.g. for repeated fields)
            val rawParts: Seq[RawPart] = codec.asInstanceOf[CodecForMany[Any, _, _]].encode(part.body).map { b => part.copy(body = b) }

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
      override val meta: CodecMeta[Seq[AnyPart], CodecFormat.MultipartFormData, Seq[RawPart]] =
        CodecMeta(Schema(SchemaType.SBinary), CodecFormat.MultipartFormData(), mvt)
    }

  //

  private[tapir] def decodeCookie(cookie: String): DecodeResult[List[Cookie]] = Cookie.parse(cookie) match {
    case Left(e)  => DecodeResult.Error(cookie, new RuntimeException(e))
    case Right(r) => DecodeResult.Value(r)
  }

  implicit def cookieCodec: Codec[List[Cookie], CodecFormat.TextPlain, String] =
    implicitly[Codec[String, CodecFormat.TextPlain, String]].mapDecode(decodeCookie)(Cookie.toString)

  private[tapir] def decodeCookieWithMeta(cookie: String): DecodeResult[CookieWithMeta] = CookieWithMeta.parse(cookie) match {
    case Left(e)  => DecodeResult.Error(cookie, new RuntimeException(e))
    case Right(r) => DecodeResult.Value(r)
  }

  implicit def cookieWithMetaCodec: Codec[CookieWithMeta, CodecFormat.TextPlain, String] =
    implicitly[Codec[String, CodecFormat.TextPlain, String]].mapDecode(decodeCookieWithMeta)(_.toString)
}

/**
  * A [[Codec]] which can encode to optional raw values / decode from optional *raw* values.
  * An optional raw value specifies if the raw value should be included in the output, or not.
  * Depending on the codec, decoding from an optional value might yield [[DecodeResult.Missing]].
  *
  * Should be used for inputs/outputs which can be mapped to an optional value.
  *
  * The main difference comparing to [[Codec]] is the signature of the `encode` and `rawDecode` methods. For each
  * [[Codec]], a [[CodecForOptional]] can be derived.
  */
@implicitNotFound(msg = """Cannot find a codec for type: ${T}, formatted as: ${CF}.
Did you define a codec for: ${T}?
Did you import the codecs for: ${CF}?
Is there an implicit schema for: ${T}, and all of its components?
(codecs are looked up as implicit values of type Codec[${T}, ${CF}, _];
schemas are looked up as implicit values of type Schema[${T}])
""")
trait CodecForOptional[T, CF <: CodecFormat, R] extends Decode[T, Option[R]] { outer =>
  def encode(t: T): Option[R]
  def rawDecode(s: Option[R]): DecodeResult[T]
  def meta: CodecMeta[T, CF, R]
  private[tapir] def validator: Validator[T] = meta.validator

  def validate(v: Validator[T]): CodecForOptional[T, CF, R] = new CodecForOptional[T, CF, R] {
    override def encode(t: T): Option[R] = outer.encode(t)
    override def rawDecode(s: Option[R]): DecodeResult[T] = outer.rawDecode(s)
    override def meta: CodecMeta[T, CF, R] = outer.meta.validate(v)
  }

  def mapDecode[TT](f: T => DecodeResult[TT])(g: TT => T): CodecForOptional[TT, CF, R] =
    new CodecForOptional[TT, CF, R] {
      override def encode(t: TT): Option[R] = outer.encode(g(t))
      override def rawDecode(s: Option[R]): DecodeResult[TT] = outer.rawDecode(s).flatMap(f)
      override val meta: CodecMeta[TT, CF, R] =
        outer.meta.copy(schema = outer.meta.schema.as[TT], validator = outer.meta.validator.contramap(g))
    }

  def map[TT](f: T => TT)(g: TT => T): CodecForOptional[TT, CF, R] = mapDecode[TT](f.andThen(Value.apply))(g)
}

object CodecForOptional {
  type PlainCodecForOptional[T] = CodecForOptional[T, CodecFormat.TextPlain, String]

  implicit def fromCodec[T, CF <: CodecFormat, R](implicit c: Codec[T, CF, R]): CodecForOptional[T, CF, R] =
    new CodecForOptional[T, CF, R] {
      override def encode(t: T): Option[R] = Some(c.encode(t))
      override def rawDecode(s: Option[R]): DecodeResult[T] = s match {
        case None    => DecodeResult.Missing
        case Some(h) => c.rawDecode(h)
      }
      override val meta: CodecMeta[T, CF, R] = c.meta
    }

  implicit def forOption[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForOptional[Option[T], CF, R] =
    new CodecForOptional[Option[T], CF, R] {
      override def encode(t: Option[T]): Option[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Option[R]): DecodeResult[Option[T]] = s match {
        case None     => DecodeResult.Value(None)
        case Some(ss) => tm.rawDecode(ss).map(Some(_))
      }
      override val meta: CodecMeta[Option[T], CF, R] =
        tm.meta.copy(schema = tm.meta.schema.asOptional, validator = tm.validator.asOptionElement)
    }
}

/**
  * A [[Codec]] which can encode to multiple (0..n) raw values / decode from multiple raw values.
  * An multiple raw value specifies that the raw values should be included in the output multiple times.
  * Depending on the codec, decoding from a multiple value might yield [[DecodeResult.Missing]] or [[DecodeResult.Multiple]].
  *
  * Should be used for inputs/outputs which can be mapped to a multiple values.
  *
  * The main difference comparing to [[Codec]] is the signature of the `encode` and `rawDecode` methods. For each
  * [[Codec]], a [[CodecForMany]] can be derived.
  */
@implicitNotFound(msg = """Cannot find a codec for type: ${T}, formatted as: ${CF}.
Did you define a codec for: ${T}?
Did you import the codecs for: ${CF}?
Is there an implicit schema for: ${T}, and all of its components?
(codecs are looked up as implicit values of type Codec[${T}, ${CF}, _];
schemas are looked up as implicit values of type Schema[${T}])
""")
trait CodecForMany[T, CF <: CodecFormat, R] extends Decode[T, Seq[R]] { outer =>
  def encode(t: T): Seq[R]
  def rawDecode(s: Seq[R]): DecodeResult[T]
  def meta: CodecMeta[T, CF, R]
  private[tapir] def validator: Validator[T] = meta.validator

  def validate(v: Validator[T]): CodecForMany[T, CF, R] = new CodecForMany[T, CF, R] {
    override def encode(t: T): Seq[R] = outer.encode(t)
    override def rawDecode(s: Seq[R]): DecodeResult[T] = outer.rawDecode(s)
    override def meta: CodecMeta[T, CF, R] = outer.meta.validate(v)
  }

  def mapDecode[TT](f: T => DecodeResult[TT])(g: TT => T): CodecForMany[TT, CF, R] =
    new CodecForMany[TT, CF, R] {
      override def encode(t: TT): Seq[R] = outer.encode(g(t))
      override def rawDecode(s: Seq[R]): DecodeResult[TT] = outer.rawDecode(s).flatMap(f)
      override val meta: CodecMeta[TT, CF, R] =
        outer.meta.copy(schema = outer.meta.schema.as[TT], validator = outer.meta.validator.contramap(g))
    }

  def map[TT](f: T => TT)(g: TT => T): CodecForMany[TT, CF, R] = mapDecode[TT](f.andThen(Value.apply))(g)
}

object CodecForMany {
  type PlainCodecForMany[T] = CodecForMany[T, CodecFormat.TextPlain, String]

  implicit def fromCodec[T, CF <: CodecFormat, R](implicit c: Codec[T, CF, R]): CodecForMany[T, CF, R] = new CodecForMany[T, CF, R] {
    override def encode(t: T): Seq[R] = List(c.encode(t))
    override def rawDecode(s: Seq[R]): DecodeResult[T] = s match {
      case Nil     => DecodeResult.Missing
      case List(h) => c.rawDecode(h)
      case l       => DecodeResult.Multiple(l)
    }
    override val meta: CodecMeta[T, CF, R] = c.meta
  }

  implicit def forOption[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[Option[T], CF, R] =
    new CodecForMany[Option[T], CF, R] {
      override def encode(t: Option[T]): Seq[R] = t.map(v => tm.encode(v)).toList
      override def rawDecode(s: Seq[R]): DecodeResult[Option[T]] = s match {
        case Nil     => DecodeResult.Value(None)
        case List(h) => tm.rawDecode(h).map(Some(_))
        case l       => DecodeResult.Multiple(l)
      }
      override val meta: CodecMeta[Option[T], CF, R] =
        tm.meta.copy(schema = tm.meta.schema.asOptional, validator = tm.meta.validator.asOptionElement)
    }

  // collections

  implicit def forSeq[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[Seq[T], CF, R] =
    new CodecForMany[Seq[T], CF, R] {
      override def encode(t: Seq[T]): Seq[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Seq[R]): DecodeResult[Seq[T]] = DecodeResult.sequence(s.map(tm.rawDecode))
      override val meta: CodecMeta[Seq[T], CF, R] =
        tm.meta.copy(schema = tm.meta.schema.asArrayElement, validator = tm.validator.asIterableElements)
    }

  implicit def forList[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[List[T], CF, R] =
    new CodecForMany[List[T], CF, R] {
      override def encode(t: List[T]): Seq[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Seq[R]): DecodeResult[List[T]] = DecodeResult.sequence(s.map(tm.rawDecode)).map(_.toList)
      override val meta: CodecMeta[List[T], CF, R] =
        tm.meta.copy(schema = tm.meta.schema.asArrayElement, validator = tm.validator.asIterableElements)
    }

  implicit def forVector[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[Vector[T], CF, R] =
    new CodecForMany[Vector[T], CF, R] {
      override def encode(t: Vector[T]): Seq[R] = t.map(v => tm.encode(v))
      override def rawDecode(s: Seq[R]): DecodeResult[Vector[T]] = DecodeResult.sequence(s.map(tm.rawDecode)).map(_.toVector)
      override val meta: CodecMeta[Vector[T], CF, R] =
        tm.meta.copy(schema = tm.meta.schema.asArrayElement, validator = tm.validator.asIterableElements)
    }

  implicit def forSet[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[Set[T], CF, R] =
    new CodecForMany[Set[T], CF, R] {
      override def encode(t: Set[T]): Seq[R] = t.map(v => tm.encode(v)).toSeq
      override def rawDecode(s: Seq[R]): DecodeResult[Set[T]] = DecodeResult.sequence(s.map(tm.rawDecode)).map(_.toSet)
      override val meta: CodecMeta[Set[T], CF, R] =
        tm.meta.copy(schema = tm.meta.schema.asArrayElement, validator = tm.validator.asIterableElements)
    }

  //

  implicit def cookiePairCodecForMany: CodecForMany[List[Cookie], CodecFormat.TextPlain, String] =
    implicitly[CodecForMany[List[List[Cookie]], CodecFormat.TextPlain, String]].map(_.flatten)(List(_))

  implicit def cookieValueWithMetaCodecForMany(name: String): CodecForMany[CookieValueWithMeta, CodecFormat.TextPlain, String] = {
    def findNamed(name: String)(cs: Seq[CookieWithMeta]): DecodeResult[CookieValueWithMeta] = {
      cs.filter(_.name == name) match {
        case Nil     => DecodeResult.Missing
        case List(c) => DecodeResult.Value(c.valueWithMeta)
        case l       => DecodeResult.Multiple(l.map(_.toString))
      }
    }

    implicitly[CodecForMany[List[String], CodecFormat.TextPlain, String]]
      .mapDecode(vs => DecodeResult.sequence(vs.map(Codec.decodeCookieWithMeta)).flatMap(findNamed(name)))(cv =>
        List(CookieWithMeta(name, cv).toString)
      )
  }
}

/**
  * Contains meta-data for a [[Codec]], between type `T` and a raw value `R`.
  *
  * The meta-data consists of the schema for type `T`, validator and reified type of the raw value.
  */
case class CodecMeta[T, CF <: CodecFormat, R] private (
    schema: Schema[T],
    format: CF,
    rawValueType: RawValueType[R],
    validator: Validator[T]
) {
  def validate(v: Validator[T]): CodecMeta[T, CF, R] = {
    copy(validator = validator.and(v))
  }
}
object CodecMeta {
  def apply[T, CF <: CodecFormat, R](
      schema: Schema[T],
      mediaType: CF,
      rawValueType: RawValueType[R],
      validator: Validator[T] = Validator.pass[T]
  ): CodecMeta[T, CF, R] =
    new CodecMeta(schema, mediaType, rawValueType, validator)
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

trait Decode[T, R] {
  def rawDecode(s: R): DecodeResult[T]

  private[tapir] def validator: Validator[T]

  /**
    * - calls `rawDecode`
    * - catches any exceptions that might occur, converting them to decode failures
    * - validates the result
    */
  def decode(r: R): DecodeResult[T] = validate(tryRawDecode(r))

  private def tryRawDecode(f: R): DecodeResult[T] = {
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
