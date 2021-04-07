package sttp.tapir

import java.io.InputStream
import java.math.{BigDecimal => JBigDecimal}
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import java.time._
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.util.{Base64, Date, UUID}
import sttp.model._
import sttp.model.headers.{Cookie, CookieWithMeta}
import sttp.tapir.CodecFormat.{MultipartFormData, OctetStream, TextPlain, XWwwFormUrlencoded}
import sttp.tapir.DecodeResult._
import sttp.tapir.RawBodyType.StringBody
import sttp.tapir.generic.internal.{FormCodecDerivation, MultipartCodecDerivation}
import sttp.tapir.internal._
import sttp.tapir.model.UsernamePassword
import sttp.ws.WebSocketFrame

import scala.annotation.implicitNotFound
import scala.concurrent.duration.{Duration => SDuration}

/** A bi-directional mapping between low-level values of type `L` and high-level values of type `H`. Low level values
  * are formatted as `CF`.
  *
  * The mapping consists of a pair of functions, one to decode (`L => H`), and one to encode (`H => L`)
  * Decoding can fail, and this is represented as a result of type [[DecodeResult]].
  *
  * A codec also contains optional meta-data on the `schema` of the high-level value, as well as an instance
  * of the format (which determines the media type of the low-level value).
  *
  * Codec instances are used as implicit values, and are looked up when defining endpoint inputs/outputs. Depending
  * on a particular endpoint input/output, it might require a codec which uses a specific format, or a specific
  * low-level value.
  *
  * Codec instances can be derived basing on other values (e.g. such as json encoders/decoders when integrating with
  * json libraries). Or, they can be defined by hand for custom types, usually customising an existing, simpler codec.
  *
  * Codecs can be chained with [[Mapping]]s using the `map` function.
  *
  * @tparam L The type of the low-level value.
  * @tparam H The type of the high-level value.
  * @tparam CF The format of encoded values. Corresponds to the media type.
  */
@implicitNotFound(msg = """Cannot find a codec between types: ${L} and ${H}, formatted as: ${CF}.
Did you define a codec for: ${H}?
Did you import the codecs for: ${CF}?
""")
trait Codec[L, H, +CF <: CodecFormat] { outer =>
  // similar to Mapping

  def rawDecode(l: L): DecodeResult[H]
  def encode(h: H): L

  /** - calls `rawDecode`
    * - catches any exceptions that might occur, converting them to decode failures
    * - validates the result
    */
  def decode(l: L): DecodeResult[H] = (Mapping.decode(l, rawDecode, schema.applyValidation), schema.default) match {
    case (DecodeResult.Missing, Some((d, _))) => DecodeResult.Value(d)
    case (r, _)                               => r
  }

  //

  def schema: Schema[H]
  def format: CF

  def map[HH](mapping: Mapping[H, HH]): Codec[L, HH, CF] =
    new Codec[L, HH, CF] {
      override def rawDecode(l: L): DecodeResult[HH] = outer.rawDecode(l).flatMap(mapping.rawDecode)
      override def encode(hh: HH): L = outer.encode(mapping.encode(hh))
      override def schema: Schema[HH] = outer.schema
        .map(v =>
          mapping.decode(v) match {
            case _: Failure => None
            case Value(v)   => Some(v)
          }
        )(mapping.encode)
        .validate(mapping.validator)
      override def format: CF = outer.format
    }

  def mapDecode[HH](f: H => DecodeResult[HH])(g: HH => H): Codec[L, HH, CF] = map(Mapping.fromDecode(f)(g))
  def map[HH](f: H => HH)(g: HH => H): Codec[L, HH, CF] = mapDecode(f.andThen(Value(_)))(g)

  def schema(s2: Schema[H]): Codec[L, H, CF] =
    new Codec[L, H, CF] {
      override def rawDecode(l: L): DecodeResult[H] = outer.decode(l)
      override def encode(h: H): L = outer.encode(h)
      override def schema: Schema[H] = s2
      override def format: CF = outer.format
    }
  def schema(s2: Option[Schema[H]]): Codec[L, H, CF] = s2.map(schema).getOrElse(this)
  def schema(modify: Schema[H] => Schema[H]): Codec[L, H, CF] = schema(modify(schema))

  def format[CF2 <: CodecFormat](f: CF2): Codec[L, H, CF2] =
    new Codec[L, H, CF2] {
      override def rawDecode(l: L): DecodeResult[H] = outer.decode(l)
      override def encode(h: H): L = outer.encode(h)
      override def schema: Schema[H] = outer.schema
      override def format: CF2 = f
    }

  def validate(v: Validator[H]): Codec[L, H, CF] = schema(schema.validate(Mapping.addEncodeToEnumValidator(v, encode)))
  def validateOption[U](v: Validator[U])(implicit hIsOptionU: H =:= Option[U]): Codec[L, H, CF] =
    schema(_.modifyUnsafe[U](Schema.ModifyCollectionElements)(_.validate(v)))
  def validateIterable[C[X] <: Iterable[X], U](v: Validator[U])(implicit hIsCU: H =:= C[U]): Codec[L, H, CF] =
    schema(_.modifyUnsafe[U](Schema.ModifyCollectionElements)(_.validate(v)))
}

object Codec extends CodecExtensions with FormCodecDerivation {
  type PlainCodec[T] = Codec[String, T, CodecFormat.TextPlain]
  type JsonCodec[T] = Codec[String, T, CodecFormat.Json]
  type XmlCodec[T] = Codec[String, T, CodecFormat.Xml]

  def id[L, CF <: CodecFormat](f: CF, s: Schema[L]): Codec[L, L, CF] =
    new Codec[L, L, CF] {
      override def rawDecode(l: L): DecodeResult[L] = Value(l)
      override def encode(h: L): L = h
      override def schema: Schema[L] = s
      override def format: CF = f
    }
  def idPlain[L](s: Schema[L] = Schema[L](SchemaType.SString())): Codec[L, L, CodecFormat.TextPlain] = id(CodecFormat.TextPlain(), s)

  implicit val string: Codec[String, String, TextPlain] = id[String, TextPlain](TextPlain(), Schema.schemaForString)

  implicit val byte: Codec[String, Byte, TextPlain] = stringCodec[Byte](_.toByte).schema(Schema.schemaForByte)
  implicit val short: Codec[String, Short, TextPlain] = stringCodec[Short](_.toShort).schema(Schema.schemaForShort)
  implicit val int: Codec[String, Int, TextPlain] = stringCodec[Int](_.toInt).schema(Schema.schemaForInt)
  implicit val long: Codec[String, Long, TextPlain] = stringCodec[Long](_.toLong).schema(Schema.schemaForLong)
  implicit val float: Codec[String, Float, TextPlain] = stringCodec[Float](_.toFloat).schema(Schema.schemaForFloat)
  implicit val double: Codec[String, Double, TextPlain] = stringCodec[Double](_.toDouble).schema(Schema.schemaForDouble)
  implicit val boolean: Codec[String, Boolean, TextPlain] = stringCodec[Boolean](_.toBoolean).schema(Schema.schemaForBoolean)
  implicit val uuid: Codec[String, UUID, TextPlain] = stringCodec[UUID](UUID.fromString).schema(Schema.schemaForUUID)
  implicit val bigDecimal: Codec[String, BigDecimal, TextPlain] = stringCodec[BigDecimal](BigDecimal(_)).schema(Schema.schemaForBigDecimal)
  implicit val javaBigDecimal: Codec[String, JBigDecimal, TextPlain] =
    stringCodec[JBigDecimal](new JBigDecimal(_)).schema(Schema.schemaForJBigDecimal)
  implicit val localTime: Codec[String, LocalTime, TextPlain] =
    string.map(LocalTime.parse(_))(DateTimeFormatter.ISO_LOCAL_TIME.format).schema(Schema.schemaForLocalTime)
  implicit val localDate: Codec[String, LocalDate, TextPlain] =
    string.map(LocalDate.parse(_))(DateTimeFormatter.ISO_LOCAL_DATE.format).schema(Schema.schemaForLocalDate)
  implicit val offsetDateTime: Codec[String, OffsetDateTime, TextPlain] =
    string.map(OffsetDateTime.parse(_))(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format).schema(Schema.schemaForOffsetDateTime)
  implicit val zonedDateTime: Codec[String, ZonedDateTime, TextPlain] =
    string.map(ZonedDateTime.parse(_))(DateTimeFormatter.ISO_ZONED_DATE_TIME.format).schema(Schema.schemaForZonedDateTime)
  implicit val instant: Codec[String, Instant, TextPlain] =
    string.map(Instant.parse(_))(DateTimeFormatter.ISO_INSTANT.format).schema(Schema.schemaForInstant)
  implicit val date: Codec[String, Date, TextPlain] = instant.map(Date.from(_))(_.toInstant).schema(Schema.schemaForDate)
  implicit val zoneOffset: Codec[String, ZoneOffset, TextPlain] = stringCodec[ZoneOffset](ZoneOffset.of).schema(Schema.schemaForZoneOffset)
  implicit val duration: Codec[String, Duration, TextPlain] = stringCodec[Duration](Duration.parse).schema(Schema.schemaForJavaDuration)
  implicit val offsetTime: Codec[String, OffsetTime, TextPlain] =
    string.map(OffsetTime.parse(_))(DateTimeFormatter.ISO_OFFSET_TIME.format).schema(Schema.schemaForOffsetTime)
  implicit val scalaDuration: Codec[String, SDuration, TextPlain] =
    stringCodec[SDuration](SDuration.apply).schema(Schema.schemaForScalaDuration)
  implicit val localDateTime: Codec[String, LocalDateTime, TextPlain] = string
    .mapDecode { l =>
      try {
        try {
          Value(LocalDateTime.parse(l))
        } catch {
          case _: DateTimeParseException => Value(OffsetDateTime.parse(l).toLocalDateTime)
        }
      } catch {
        case e: Exception => Error(l, e)
      }
    }(h => OffsetDateTime.of(h, ZoneOffset.UTC).toString)
    .schema(Schema.schemaForLocalDateTime)
  implicit val uri: PlainCodec[Uri] =
    string.mapDecode(raw => Uri.parse(raw).fold(e => DecodeResult.Error(raw, new IllegalArgumentException(e)), DecodeResult.Value(_)))(
      _.toString()
    )

  def stringCodec[T: Schema](parse: String => T): Codec[String, T, TextPlain] =
    string.map(parse)(_.toString).schema(implicitly[Schema[T]])

  implicit val byteArray: Codec[Array[Byte], Array[Byte], OctetStream] =
    id[Array[Byte], OctetStream](OctetStream(), Schema.schemaForByteArray)
  implicit val inputStream: Codec[InputStream, InputStream, OctetStream] =
    id[InputStream, OctetStream](OctetStream(), Schema.schemaForInputStream)
  implicit val byteBuffer: Codec[ByteBuffer, ByteBuffer, OctetStream] =
    id[ByteBuffer, OctetStream](OctetStream(), Schema.schemaForByteBuffer)

  implicit val formSeqCodecUtf8: Codec[String, Seq[(String, String)], XWwwFormUrlencoded] = formSeqCodec(StandardCharsets.UTF_8)
  implicit val formMapCodecUtf8: Codec[String, Map[String, String], XWwwFormUrlencoded] = formMapCodec(StandardCharsets.UTF_8)

  def formSeqCodec(charset: Charset): Codec[String, Seq[(String, String)], XWwwFormUrlencoded] =
    string.format(XWwwFormUrlencoded()).map(UrlencodedData.decode(_, charset))(UrlencodedData.encode(_, charset))
  def formMapCodec(charset: Charset): Codec[String, Map[String, String], XWwwFormUrlencoded] =
    formSeqCodec(charset).map(_.toMap)(_.toSeq)

  def rawPartCodec(
      partCodecs: Map[String, PartCodec[_, _]],
      defaultCodec: Option[PartCodec[_, _]]
  ): Codec[Seq[RawPart], Seq[AnyPart], MultipartFormData] =
    new Codec[Seq[RawPart], Seq[AnyPart], MultipartFormData] {
      private def partCodec(name: String): Option[PartCodec[_, _]] = partCodecs.get(name).orElse(defaultCodec)

      override def encode(t: Seq[AnyPart]): Seq[RawPart] = {
        t.flatMap { part =>
          partCodec(part.name).toList.flatMap { case PartCodec(rawBodyType, codec) =>
            // a single value-part might yield multiple raw-parts (e.g. for repeated fields)
            val rawParts: Seq[RawPart] =
              codec.asInstanceOf[Codec[List[_], Any, _]].encode(part.body).map { b =>
                val p = part.copy(body = b)
                // setting the content type basing on the format, if it's not yet defined
                p.contentType match {
                  case None =>
                    (codec.format.mediaType, rawBodyType) match {
                      // only text parts can have a charset
                      case (s, StringBody(e)) if s.mainType.equalsIgnoreCase("text") => p.contentType(codec.format.mediaType.charset(e))
                      case _                                                         => p.contentType(codec.format.mediaType)
                    }
                  case _ => p
                }
              }
            rawParts
          }
        }
      }
      override def rawDecode(l: Seq[RawPart]): DecodeResult[Seq[AnyPart]] = {
        val rawPartsByName = l.groupBy(_.name)

        // we need to decode all parts for which there's a codec defined (even if that part is missing a value -
        // it might still decode to e.g. None), and if there's a default codec also the extra parts
        val partNamesToDecode = partCodecs.keys.toSet ++ (if (defaultCodec.isDefined) rawPartsByName.keys.toSet else Set.empty)

        // there might be multiple raw-parts for each name, yielding a single value-part
        val anyParts: List[DecodeResult[AnyPart]] = partNamesToDecode.map { name =>
          val codec = partCodec(name).get.codec
          val rawParts = rawPartsByName.get(name).toList.flatten
          codec.asInstanceOf[Codec[List[_], Any, _]].rawDecode(rawParts.map(_.body)).map { body =>
            // we know there's at least one part. Using this part to create the value-part
            rawParts.headOption match {
              case Some(rawPart) => rawPart.copy(body = body)
              case None          => Part(name, body)
            }
          }
        }.toList

        DecodeResult.sequence(anyParts)
      }

      override def schema: Schema[Seq[RawPart]] = Schema.binary
      override def format: MultipartFormData = CodecFormat.MultipartFormData()
    }

  /** @param partCodecs For each supported part, a (raw body type, codec) pair which encodes the part value into a
    *                   raw value of the given type. A single part value might be encoded as multiple (or none) raw
    *                   values.
    * @param defaultPartCodec Default codec to use for parts which are not defined in `partCodecs`. `None`, if extra
    *                         parts should be discarded.
    */
  def multipartCodec(
      partCodecs: Map[String, PartCodec[_, _]],
      defaultPartCodec: Option[PartCodec[_, _]]
  ): MultipartCodec[Seq[AnyPart]] =
    MultipartCodec(
      RawBodyType.MultipartBody(partCodecs.map(t => (t._1, t._2.rawBodyType)).toMap, defaultPartCodec.map(_.rawBodyType)),
      rawPartCodec(partCodecs, defaultPartCodec)
    )

  //

  private[tapir] def decodeCookie(cookie: String): DecodeResult[List[Cookie]] =
    Cookie.parse(cookie) match {
      case Left(e)  => DecodeResult.Error(cookie, new RuntimeException(e))
      case Right(r) => DecodeResult.Value(r)
    }

  implicit val cookieCodec: Codec[String, List[Cookie], TextPlain] = Codec.string.mapDecode(decodeCookie)(cs => Cookie.toString(cs))
  implicit val cookiesCodec: Codec[List[String], List[Cookie], TextPlain] = Codec.list(cookieCodec).map(_.flatten)(List(_))

  private[tapir] def decodeCookieWithMeta(cookie: String): DecodeResult[CookieWithMeta] =
    CookieWithMeta.parse(cookie) match {
      case Left(e)  => DecodeResult.Error(cookie, new RuntimeException(e))
      case Right(r) => DecodeResult.Value(r)
    }

  implicit val cookieWithMetaCodec: Codec[String, CookieWithMeta, TextPlain] = Codec.string.mapDecode(decodeCookieWithMeta)(_.toString)
  implicit val cookiesWithMetaCodec: Codec[List[String], List[CookieWithMeta], TextPlain] = Codec.list(cookieWithMetaCodec)

  //

  implicit def usernamePasswordCodec: PlainCodec[UsernamePassword] = {
    def decode(s: String): DecodeResult[UsernamePassword] =
      try {
        val s2 = new String(Base64.getDecoder.decode(s))
        val up = s2.split(":", 2) match {
          case Array()      => UsernamePassword("", None)
          case Array(u)     => UsernamePassword(u, None)
          case Array(u, "") => UsernamePassword(u, None)
          case Array(u, p)  => UsernamePassword(u, Some(p))
        }
        DecodeResult.Value(up)
      } catch {
        case e: Exception => DecodeResult.Error(s, e)
      }

    def encode(up: UsernamePassword): String =
      Base64.getEncoder.encodeToString(s"${up.username}:${up.password.getOrElse("")}".getBytes("UTF-8"))

    Codec.string.mapDecode(decode)(encode)
  }

  //

  implicit val webSocketFrameCodec: Codec[WebSocketFrame, WebSocketFrame, CodecFormat.TextPlain] = Codec.idPlain()

  /** A codec which expects only text frames (all other frames cause a decoding error) and handles the text using
    * the given `stringCodec`.
    */
  implicit def textWebSocketFrameCodec[A, CF <: CodecFormat](implicit
      stringCodec: Codec[String, A, CF]
  ): Codec[WebSocketFrame, A, CF] =
    Codec
      .id[WebSocketFrame, CF](stringCodec.format, Schema.string)
      .mapDecode {
        case WebSocketFrame.Text(p, _, _) => stringCodec.decode(p)
        case f                            => DecodeResult.Error(f.toString, new UnsupportedWebSocketFrameException(f))
      }(a => WebSocketFrame.text(stringCodec.encode(a)))
      .schema(stringCodec.schema)

  /** A codec which expects only text and close frames (all other frames cause a decoding error). Close frames
    * correspond to `None`, while text frames are handled using the given `stringCodec` and wrapped with `Some`.
    */
  implicit def textOrCloseWebSocketFrameCodec[A, CF <: CodecFormat](implicit
      stringCodec: Codec[String, A, CF]
  ): Codec[WebSocketFrame, Option[A], CF] =
    Codec
      .id[WebSocketFrame, CF](stringCodec.format, Schema.string)
      .mapDecode {
        case WebSocketFrame.Text(p, _, _) => stringCodec.decode(p).map(Some(_))
        case WebSocketFrame.Close(_, _)   => DecodeResult.Value(None)
        case f                            => DecodeResult.Error(f.toString, new UnsupportedWebSocketFrameException(f))
      } {
        case None    => WebSocketFrame.close
        case Some(a) => WebSocketFrame.text(stringCodec.encode(a))
      }
      .schema(stringCodec.schema.asOption)

  /** A codec which expects only binary frames (all other frames cause a decoding error) and handles the text using
    * the given `byteArrayCodec`.
    */
  implicit def binaryWebSocketFrameCodec[A, CF <: CodecFormat](implicit
      byteArrayCodec: Codec[Array[Byte], A, CF]
  ): Codec[WebSocketFrame, A, CF] =
    Codec
      .id[WebSocketFrame, CF](byteArrayCodec.format, Schema.binary)
      .mapDecode {
        case WebSocketFrame.Binary(p, _, _) => byteArrayCodec.decode(p)
        case f                              => DecodeResult.Error(f.toString, new UnsupportedWebSocketFrameException(f))
      }(a => WebSocketFrame.binary(byteArrayCodec.encode(a)))
      .schema(byteArrayCodec.schema)

  /** A codec which expects only binary and close frames (all other frames cause a decoding error). Close frames
    * correspond to `None`, while text frames are handled using the given `byteArrayCodec` and wrapped with `Some`.
    */
  implicit def binaryOrCloseWebSocketFrameCodec[A, CF <: CodecFormat](implicit
      byteArrayCodec: Codec[Array[Byte], A, CF]
  ): Codec[WebSocketFrame, Option[A], CF] =
    Codec
      .id[WebSocketFrame, CF](byteArrayCodec.format, Schema.binary)
      .mapDecode {
        case WebSocketFrame.Binary(p, _, _) => byteArrayCodec.decode(p).map(Some(_))
        case WebSocketFrame.Close(_, _)     => DecodeResult.Value(None)
        case f                              => DecodeResult.Error(f.toString, new UnsupportedWebSocketFrameException(f))
      } {
        case None    => WebSocketFrame.close
        case Some(a) => WebSocketFrame.binary(byteArrayCodec.encode(a))
      }
      .schema(byteArrayCodec.schema.asOption)

  //

  private def listBinarySchema[T, U, CF <: CodecFormat](c: Codec[T, U, CF]): Codec[List[T], List[U], CF] =
    id[List[T], CF](c.format, Schema.binary)
      .mapDecode(ts => DecodeResult.sequence(ts.map(c.decode)).map(_.toList))(us => us.map(c.encode))

  /** Create a codec which decodes/encodes a list of low-level values to a list of high-level values, using the given
    * base codec `c`.
    *
    * The schema is copied from the base codec.
    */
  implicit def list[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], List[U], CF] =
    listBinarySchema(c).schema(c.schema.asIterable[List])

  /** Create a codec which decodes/encodes a list of low-level values to a set of high-level values, using the given
    * base codec `c`.
    *
    * The schema is copied from the base codec.
    */
  implicit def set[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], Set[U], CF] =
    Codec
      .id[List[T], CF](c.format, Schema.binary)
      .mapDecode(ts => DecodeResult.sequence(ts.map(c.decode)).map(_.toSet))(us => us.map(c.encode).toList)
      .schema(c.schema.asIterable[Set])

  /** Create a codec which decodes/encodes a list of low-level values to a vector of high-level values, using the given
    * base codec `c`.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def vector[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], Vector[U], CF] =
    Codec
      .id[List[T], CF](c.format, Schema.binary)
      .mapDecode(ts => DecodeResult.sequence(ts.map(c.decode)).map(_.toVector))(us => us.map(c.encode).toList)
      .schema(c.schema.asIterable[Vector])

  /** Create a codec which requires that a list of low-level values contains a single element. Otherwise a decode
    * failure is returned. The given base codec `c` is used for decoding/encoding.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def listHead[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], U, CF] =
    listBinarySchema(c)
      .mapDecode({
        case Nil     => DecodeResult.Missing
        case List(e) => DecodeResult.Value(e)
        case l       => DecodeResult.Multiple(l)
      })(List(_))
      .schema(c.schema)

  /** Create a codec which requires that a list of low-level values is empty or contains a single element. If it
    * contains multiple elements, a decode failure is returned. The given base codec `c` is used for decoding/encoding.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def listHeadOption[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], Option[U], CF] =
    listBinarySchema(c)
      .mapDecode({
        case Nil     => DecodeResult.Value(None)
        case List(e) => DecodeResult.Value(Some(e))
        case l       => DecodeResult.Multiple(l.map(_.toString))
      })(_.toList)
      .schema(c.schema.asOption)

  /** Create a codec which requires that an optional low-level value is defined. If it is `None`, a decode failure is
    * returned. The given base codec `c` is used for decoding/encoding.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def optionHead[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[Option[T], U, CF] =
    id[Option[T], CF](c.format, Schema.binary)
      .mapDecode({
        case None    => DecodeResult.Missing
        case Some(e) => c.decode(e)
      })(u => Some(c.encode(u)))
      .schema(c.schema)

  /** Create a codec which decodes/encodes an optional low-level value to an optional high-level value. The given
    * base codec `c` is used for decoding/encoding.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def option[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[Option[T], Option[U], CF] =
    id[Option[T], CF](c.format, Schema.binary)
      .mapDecode {
        case None    => DecodeResult.Value(None)
        case Some(v) => c.decode(v).map(Some(_))
      }(us => us.map(c.encode))
      .schema(c.schema.asOption)

  def fromDecodeAndMeta[L, H: Schema, CF <: CodecFormat](cf: CF)(f: L => DecodeResult[H])(g: H => L): Codec[L, H, CF] =
    new Codec[L, H, CF] {
      override def rawDecode(l: L): DecodeResult[H] = f(l)
      override def encode(h: H): L = g(h)
      override def schema: Schema[H] = implicitly[Schema[H]]
      override def format: CF = cf
    }

  def json[T: Schema](_rawDecode: String => DecodeResult[T])(_encode: T => String): JsonCodec[T] = {
    anyStringCodec(CodecFormat.Json())(_rawDecode)(_encode)
  }

  def xml[T: Schema](_rawDecode: String => DecodeResult[T])(_encode: T => String): XmlCodec[T] = {
    anyStringCodec(CodecFormat.Xml())(_rawDecode)(_encode)
  }

  def anyStringCodec[T: Schema, CF <: CodecFormat](
      cf: CF
  )(_rawDecode: String => DecodeResult[T])(_encode: T => String): Codec[String, T, CF] = {
    val isOptional = implicitly[Schema[T]].isOptional
    fromDecodeAndMeta(cf)({ (s: String) =>
      val toDecode = if (isOptional && s == "") "null" else s
      _rawDecode(toDecode)
    })(t => if (isOptional && t == None) "" else _encode(t))
  }
}

/** Information needed to read a single part of a multipart body: the raw type (`rawBodyType`), and the codec
  * which further decodes it.
  */
case class PartCodec[R, T](rawBodyType: RawBodyType[R], codec: Codec[List[R], T, _ <: CodecFormat])

case class MultipartCodec[T](rawBodyType: RawBodyType.MultipartBody, codec: Codec[Seq[RawPart], T, CodecFormat.MultipartFormData]) {
  def map[U](mapping: Mapping[T, U]): MultipartCodec[U] = MultipartCodec(rawBodyType, codec.map(mapping))
  def map[U](f: T => U)(g: U => T): MultipartCodec[U] = map(Mapping.from(f)(g))
  def mapDecode[U](f: T => DecodeResult[U])(g: U => T): MultipartCodec[U] = map(Mapping.fromDecode(f)(g))

  def schema(s: Schema[T]): MultipartCodec[T] = copy(codec = codec.schema(s))
  def validate(v: Validator[T]): MultipartCodec[T] = copy(codec = codec.validate(v))
}

object MultipartCodec extends MultipartCodecDerivation {
  val Default: MultipartCodec[Seq[Part[Array[Byte]]]] = {
    Codec
      .multipartCodec(Map.empty, Some(PartCodec(RawBodyType.ByteArrayBody, Codec.listHead(Codec.byteArray))))
      .asInstanceOf[MultipartCodec[Seq[Part[Array[Byte]]]]] // we know that all parts will end up as byte arrays
  }
}

/** The raw format of the body: what do we need to know, to read it and pass to a codec for further decoding.
  */
sealed trait RawBodyType[R]
object RawBodyType {
  case class StringBody(charset: Charset) extends RawBodyType[String]

  sealed trait Binary[R] extends RawBodyType[R]
  implicit case object ByteArrayBody extends Binary[Array[Byte]]
  implicit case object ByteBufferBody extends Binary[ByteBuffer]
  implicit case object InputStreamBody extends Binary[InputStream]
  implicit case object FileBody extends Binary[TapirFile]

  case class MultipartBody(partTypes: Map[String, RawBodyType[_]], defaultType: Option[RawBodyType[_]]) extends RawBodyType[Seq[RawPart]] {
    def partType(name: String): Option[RawBodyType[_]] = partTypes.get(name).orElse(defaultType)
  }
}
