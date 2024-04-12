package sttp.tapir

import sttp.model._
import sttp.model.headers.{CacheDirective, ContentRange, Cookie, CookieWithMeta, ETag, Range}
import sttp.tapir.CodecFormat.{MultipartFormData, OctetStream, TextPlain, XWwwFormUrlencoded}
import sttp.tapir.DecodeResult.Error.MultipartDecodeException
import sttp.tapir.DecodeResult._
import sttp.tapir.RawBodyType.StringBody
import sttp.tapir.internal._
import sttp.tapir.macros.{CodecMacros, FormCodecMacros, MultipartCodecMacros}
import sttp.tapir.model.{UnsupportedWebSocketFrameException, UsernamePassword}
import sttp.ws.WebSocketFrame

import java.io.InputStream
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import java.time._
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.util.{Base64, Date, UUID}
import scala.annotation.{implicitNotFound, tailrec}
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{Duration => SDuration}

/** A bi-directional mapping between low-level values of type `L` and high-level values of type `H`. Low level values are formatted as `CF`.
  *
  * The mapping consists of a pair of functions, one to decode (`L => H`), and one to encode (`H => L`). Decoding can fail, and this is
  * represented as a result of type [[DecodeResult]].
  *
  * A codec also contains optional meta-data in the `schema` of the high-level value (which includes validators), as well as an instance of
  * the format (which determines the media type of the low-level value).
  *
  * Codec instances are used as implicit values, and are looked up when defining endpoint inputs/outputs. Depending on a particular endpoint
  * input/output, it might require a codec which uses a specific format, or a specific low-level value.
  *
  * Codec instances can be derived basing on other values (e.g. such as json encoders/decoders when integrating with json libraries). Or,
  * they can be defined by hand for custom types, usually customising an existing, simpler codec.
  *
  * Codecs can be chained with [[Mapping]] s using the `map` function.
  *
  * @tparam L
  *   The type of the low-level value.
  * @tparam H
  *   The type of the high-level value.
  * @tparam CF
  *   The format of encoded values. Corresponds to the media type.
  */
@implicitNotFound(msg = """Cannot find a codec between types: ${L} and ${H}, formatted as: ${CF}.
Did you define a codec for: ${H}?
Did you import the codecs for: ${CF}?
""")
trait Codec[L, H, +CF <: CodecFormat] { outer =>
  // similar to Mapping

  def rawDecode(l: L): DecodeResult[H]
  def encode(h: H): L

  /**   - calls `rawDecode`
    *   - catches any exceptions that might occur, converting them to decode failures
    *   - validates the result
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

  /** Maps this codec to the given higher-level type `HH`.
    *
    * @param f
    *   decoding function
    * @param g
    *   encoding function
    * @tparam HH
    *   target type
    * @see
    *   [[map]]
    * @see
    *   [[mapDecode]]
    * @see
    *   [[mapValidate]]
    */
  def mapEither[HH](f: H => Either[String, HH])(g: HH => H): Codec[L, HH, CF] =
    mapDecode(s => DecodeResult.fromEitherString(s.toString, f(s)))(g)

  /** Adds the given validator to the codec's schema, and maps this codec to the given higher-level type `HH`.
    *
    * Unlike a `.validate(v).map(f)(g)` invocation, during decoding the validator is run before applying the `f` function. If there are
    * validation errors, decoding fails. However, the validator is then invoked again on the fully decoded value.
    *
    * This is useful to create codecs for types, which are unrepresentable unless the validator's condition is met, e.g. due to
    * preconditions in the constructor.
    *
    * @see
    *   [[validate]]
    */
  def mapValidate[HH](v: Validator[H])(f: H => HH)(g: HH => H): Codec[L, HH, CF] =
    validate(v).mapDecode { h =>
      v(h) match {
        case Nil    => DecodeResult.Value(f(h))
        case errors => DecodeResult.InvalidValue(errors)
      }
    }(g)

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

  /** Adds a validator to the codec's schema.
    *
    * Note that validation is run on a fully decoded value. That is, during decoding, first the decoding functions are run, followed by
    * validations. Hence any functions provided in subsequent `.map`s or `.mapDecode`s will be invoked before validation.
    *
    * @see
    *   [[mapValidate]]
    */
  def validate(v: Validator[H]): Codec[L, H, CF] = schema(schema.validate(Mapping.addEncodeToEnumValidator(v, encode)))

  /** Adds a validator which validates the option's element, if it is present.
    *
    * Note that validation is run on a fully decoded value. That is, during decoding, first the decoding functions are run, followed by
    * validations. Hence any functions provided in subsequent `.map`s or `.mapDecode`s will be invoked before validation.
    *
    * Should only be used if the schema hasn't been created by `.map`ping another one, but directly from `Schema[U]`. Otherwise the shape of
    * the schema doesn't correspond to the type `T`, but to some lower-level representation of the type. This might cause invalid results at
    * run-time.
    */
  def validateOption[U](v: Validator[U])(implicit hIsOptionU: H =:= Option[U]): Codec[L, H, CF] =
    schema(_.modifyUnsafe[U](Schema.ModifyCollectionElements)(_.validate(v)))

  /** Adds a validator which validates each element in the collection.
    *
    * Note that validation is run on a fully decoded value. That is, during decoding, first the decoding functions are run, followed by
    * validations. Hence any functions provided in subsequent `.map`s or `.mapDecode`s will be invoked before validation.
    *
    * Should only be used if the schema hasn't been created by `.map`ping another one, but directly from `Schema[U]`. Otherwise the shape of
    * the schema doesn't correspond to the type `T`, but to some lower-level representation of the type. This might cause invalid results at
    * run-time.
    */
  def validateIterable[C[X] <: Iterable[X], U](v: Validator[U])(implicit hIsCU: H =:= C[U]): Codec[L, H, CF] =
    schema(_.modifyUnsafe[U](Schema.ModifyCollectionElements)(_.validate(v)))
}

object Codec extends CodecExtensions with CodecExtensions2 with FormCodecMacros with CodecMacros with LowPriorityCodec {
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

  implicit val byte: Codec[String, Byte, TextPlain] = parsedString[Byte](_.toByte).schema(Schema.schemaForByte)
  implicit val short: Codec[String, Short, TextPlain] = parsedString[Short](_.toShort).schema(Schema.schemaForShort)
  implicit val int: Codec[String, Int, TextPlain] = parsedString[Int](_.toInt).schema(Schema.schemaForInt)
  implicit val long: Codec[String, Long, TextPlain] = parsedString[Long](_.toLong).schema(Schema.schemaForLong)
  implicit val float: Codec[String, Float, TextPlain] = parsedString[Float](_.toFloat).schema(Schema.schemaForFloat)
  implicit val double: Codec[String, Double, TextPlain] = parsedString[Double](_.toDouble).schema(Schema.schemaForDouble)
  implicit val boolean: Codec[String, Boolean, TextPlain] = parsedString[Boolean](_.toBoolean).schema(Schema.schemaForBoolean)
  implicit val uuid: Codec[String, UUID, TextPlain] = parsedString[UUID](UUID.fromString).schema(Schema.schemaForUUID)
  implicit val bigDecimal: Codec[String, BigDecimal, TextPlain] = parsedString[BigDecimal](BigDecimal(_)).schema(Schema.schemaForBigDecimal)
  implicit val javaBigDecimal: Codec[String, JBigDecimal, TextPlain] =
    parsedString[JBigDecimal](new JBigDecimal(_)).schema(Schema.schemaForJBigDecimal)
  implicit val bigInt: Codec[String, BigInt, TextPlain] = parsedString[BigInt](BigInt(_)).schema(Schema.schemaForBigInt)
  implicit val javaBigInteger: Codec[String, JBigInteger, TextPlain] =
    parsedString[JBigInteger](new JBigInteger(_)).schema(Schema.schemaForJBigInteger)
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
  implicit val zoneOffset: Codec[String, ZoneOffset, TextPlain] = parsedString[ZoneOffset](ZoneOffset.of).schema(Schema.schemaForZoneOffset)
  implicit val zoneId: Codec[String, ZoneId, TextPlain] = parsedString[ZoneId](ZoneId.of).schema(Schema.schemaForZoneId)
  implicit val duration: Codec[String, Duration, TextPlain] = parsedString[Duration](Duration.parse).schema(Schema.schemaForJavaDuration)
  implicit val offsetTime: Codec[String, OffsetTime, TextPlain] =
    string.map(OffsetTime.parse(_))(DateTimeFormatter.ISO_OFFSET_TIME.format).schema(Schema.schemaForOffsetTime)
  implicit val scalaDuration: Codec[String, SDuration, TextPlain] =
    parsedString[SDuration](SDuration.apply).schema(Schema.schemaForScalaDuration)
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
    string
      .mapDecode(raw => Uri.parse(raw).fold(e => DecodeResult.Error(raw, new IllegalArgumentException(e)), DecodeResult.Value(_)))(
        _.toString()
      )
      .schema(Schema.schemaForUri)

  def parsedString[T: Schema](parse: String => T): Codec[String, T, TextPlain] =
    string.map(parse)(_.toString).schema(implicitly[Schema[T]])

  implicit val byteArray: Codec[Array[Byte], Array[Byte], OctetStream] =
    id[Array[Byte], OctetStream](OctetStream(), Schema.schemaForByteArray)
  implicit val inputStream: Codec[InputStream, InputStream, OctetStream] =
    id[InputStream, OctetStream](OctetStream(), Schema.schemaForInputStream)
  implicit val inputStreamRange: Codec[InputStreamRange, InputStreamRange, OctetStream] =
    id[InputStreamRange, OctetStream](OctetStream(), Schema.schemaForInputStreamRange)
  implicit val byteBuffer: Codec[ByteBuffer, ByteBuffer, OctetStream] =
    id[ByteBuffer, OctetStream](OctetStream(), Schema.schemaForByteBuffer)
  implicit val fileRange: Codec[FileRange, FileRange, OctetStream] =
    id[FileRange, OctetStream](OctetStream(), Schema.schemaForFileRange)
  implicit val file: Codec[FileRange, TapirFile, OctetStream] = fileRange.map(_.file)(f => FileRange(f))

  implicit val formSeqUtf8: Codec[String, Seq[(String, String)], XWwwFormUrlencoded] = formSeq(StandardCharsets.UTF_8)
  implicit val formMapUtf8: Codec[String, Map[String, String], XWwwFormUrlencoded] = formMap(StandardCharsets.UTF_8)

  def formSeq(charset: Charset): Codec[String, Seq[(String, String)], XWwwFormUrlencoded] =
    string.format(XWwwFormUrlencoded()).map(UrlencodedData.decode(_, charset))(UrlencodedData.encode(_, charset))
  def formMap(charset: Charset): Codec[String, Map[String, String], XWwwFormUrlencoded] =
    formSeq(charset).map(_.toMap)(_.toSeq)

  def rawPart(
      partCodecs: Map[String, PartCodec[_, _]],
      defaultCodec: Option[PartCodec[_, _]]
  ): Codec[Seq[RawPart], ListMap[String, _], MultipartFormData] =
    new Codec[Seq[RawPart], ListMap[String, _], MultipartFormData] {

      private def partCodec(name: String): Option[PartCodec[_, _]] = partCodecs.get(name).orElse(defaultCodec)

      override def encode(t: ListMap[String, _]): Seq[RawPart] = {
        t.toList.flatMap { case (name, body) =>
          partCodec(name).toList.flatMap { case PartCodec(rawBodyType, codec) =>
            val partList: List[Part[Any]] = codec.asInstanceOf[Codec[List[Part[Any]], Any, _]].encode(body)
            partList.map { part =>
              val partWithContentType = withContentType(part.copy(name = name), rawBodyType, codec.format.mediaType)

              if (!part.otherDispositionParams.contains("filename")) {
                (part.body match {
                  case fileRange: FileRange => Some(TapirFile.name(fileRange.file))
                  case _                    => None
                }).map(fn => partWithContentType.fileName(fn)).getOrElse(partWithContentType)
              } else {
                partWithContentType
              }
            }
          }
        }
      }

      override def rawDecode(l: Seq[RawPart]): DecodeResult[ListMap[String, _]] = {
        val rawPartsByName = l.groupBy(_.name)

        // we need to decode all parts for which there's a codec defined (even if that part is missing a value -
        // it might still decode to e.g. None), and if there's a default codec also the extra parts
        val inputPartNamesToDecode =
          if (defaultCodec.isDefined)
            l.map(_.name)
          else
            l.map(_.name).filter(partCodecs.keys.toSet.contains(_))

        val partNamesToDecode = (inputPartNamesToDecode ++ partCodecs.keys).toList.distinct

        // there might be multiple raw-parts for each name, yielding a single value-part
        val anyParts: List[(String, DecodeResult[Any])] = partNamesToDecode.map { name =>
          val codec = partCodec(name).get.codec
          val rawParts = rawPartsByName.get(name).toList.flatten
          name -> codec.asInstanceOf[Codec[List[AnyPart], Any, _]].rawDecode(rawParts)
        }

        val partFailures = anyParts.collect { case (partName, partDecodeFailure: DecodeResult.Failure) =>
          (partName, partDecodeFailure)
        }

        if (partFailures.nonEmpty) {
          DecodeResult.Error("", MultipartDecodeException(partFailures))
        } else
          DecodeResult.Value(anyParts.collect { case (partName, DecodeResult.Value(v)) =>
            partName -> v
          }.toListMap)
      }

      override def schema: Schema[ListMap[String, _]] = Schema.binary
      override def format: MultipartFormData = CodecFormat.MultipartFormData()
    }

  private def withContentType[R, T](p: Part[T], rawBodyType: RawBodyType[R], mediaType: MediaType): Part[T] = {
    // setting the content type basing on the format, if it's not yet defined
    p.contentType match {
      case None =>
        (mediaType, rawBodyType) match {
          // only text parts can have a charset
          case (s, StringBody(e)) if s.isText => p.contentType(mediaType.charset(e))
          case _                              => p.contentType(mediaType)
        }
      case _ => p
    }
  }

  /** @param partCodecs
    *   For each supported part, a (raw body type, codec) pair which encodes the part value into a raw value of the given type. A single
    *   part value might be encoded as multiple (or none) raw values.
    * @param defaultPartCodec
    *   Default codec to use for parts which are not defined in `partCodecs`. `None`, if extra parts should be discarded.
    */
  def multipart(
      partCodecs: Map[String, PartCodec[_, _]],
      defaultPartCodec: Option[PartCodec[_, _]]
  ): MultipartCodec[ListMap[String, _]] =
    MultipartCodec(
      RawBodyType.MultipartBody(partCodecs.map(t => (t._1, t._2.rawBodyType)).toMap, defaultPartCodec.map(_.rawBodyType)),
      rawPart(partCodecs, defaultPartCodec)
    )

  implicit def usernamePassword: PlainCodec[UsernamePassword] = {
    def decode(s: String): DecodeResult[UsernamePassword] =
      try {
        val s2 = new String(Base64.getDecoder.decode(s))
        val up = s2.split(":", 2) match {
          case Array()      => UsernamePassword("", None)
          case Array(u)     => UsernamePassword(u, None)
          case Array(u, "") => UsernamePassword(u, None)
          case Array(u, p)  => UsernamePassword(u, Some(p))
          case _            => sys.error("impossible")
        }
        DecodeResult.Value(up)
      } catch {
        case e: Exception => DecodeResult.Error(s, e)
      }

    def encode(up: UsernamePassword): String =
      Base64.getEncoder.encodeToString(s"${up.username}:${up.password.getOrElse("")}".getBytes("UTF-8"))

    Codec.string.mapDecode(decode)(encode)
  }

  implicit def part[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[Part[T], Part[U], CF] = {
    id[Part[T], CF](c.format, Schema.binary)
      .mapDecode(e => c.decode(e.body).map(r => e.copy(body = r)))(e => e.copy(body = c.encode(e.body)))
  }

  implicit def unwrapPart[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[Part[T], U, CF] = {
    id[Part[T], CF](c.format, Schema.binary)
      .mapDecode(e => c.decode(e.body))(e => Part("?", c.encode(e)))
  }

  //

  implicit val webSocketFrame: Codec[WebSocketFrame, WebSocketFrame, CodecFormat.TextPlain] = Codec.idPlain()

  /** A codec which expects only text frames (all other frames cause a decoding error) and handles the text using the given `stringCodec`.
    */
  implicit def textWebSocketFrame[A, CF <: CodecFormat](implicit
      stringCodec: Codec[String, A, CF]
  ): Codec[WebSocketFrame, A, CF] =
    Codec
      .id[WebSocketFrame, CF](stringCodec.format, Schema.string)
      .mapDecode {
        case WebSocketFrame.Text(p, _, _) => stringCodec.decode(p)
        case f                            => DecodeResult.Error(f.toString, new UnsupportedWebSocketFrameException(f))
      }(a => WebSocketFrame.text(stringCodec.encode(a)))
      .schema(stringCodec.schema)

  /** A codec which expects only text and close frames (all other frames cause a decoding error). Close frames correspond to `None`, while
    * text frames are handled using the given `stringCodec` and wrapped with `Some`.
    */
  implicit def textOrCloseWebSocketFrame[A, CF <: CodecFormat](implicit
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

  /** A codec which expects only binary frames (all other frames cause a decoding error) and handles the text using the given
    * `byteArrayCodec`.
    */
  implicit def binaryWebSocketFrame[A, CF <: CodecFormat](implicit
      byteArrayCodec: Codec[Array[Byte], A, CF]
  ): Codec[WebSocketFrame, A, CF] =
    Codec
      .id[WebSocketFrame, CF](byteArrayCodec.format, Schema.binary)
      .mapDecode {
        case WebSocketFrame.Binary(p, _, _) => byteArrayCodec.decode(p)
        case f                              => DecodeResult.Error(f.toString, new UnsupportedWebSocketFrameException(f))
      }(a => WebSocketFrame.binary(byteArrayCodec.encode(a)))
      .schema(byteArrayCodec.schema)

  /** A codec which expects only binary and close frames (all other frames cause a decoding error). Close frames correspond to `None`, while
    * text frames are handled using the given `byteArrayCodec` and wrapped with `Some`.
    */
  implicit def binaryOrCloseWebSocketFrame[A, CF <: CodecFormat](implicit
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

  private[tapir] def listBinary[T, U, CF <: CodecFormat](c: Codec[T, U, CF]): Codec[List[T], List[U], CF] =
    id[List[T], CF](c.format, Schema.binary)
      .mapDecode(ts => DecodeResult.sequence(ts.map(c.decode)).map(_.toList))(us => us.map(c.encode))

  /** Create a codec which decodes/encodes a list of low-level values to a list of high-level values, using the given base codec `c`.
    *
    * The schema is copied from the base codec.
    */
  implicit def list[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], List[U], CF] =
    listBinary(c).schema(c.schema.asIterable[List])

  /** Create a codec which decodes/encodes a list of low-level values to a set of high-level values, using the given base codec `c`.
    *
    * The schema is copied from the base codec.
    */
  implicit def set[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], Set[U], CF] =
    Codec
      .id[List[T], CF](c.format, Schema.binary)
      .mapDecode(ts => DecodeResult.sequence(ts.map(c.decode)).map(_.toSet))(us => us.map(c.encode).toList)
      .schema(c.schema.asIterable[Set])

  /** Create a codec which decodes/encodes a list of low-level values to a vector of high-level values, using the given base codec `c`.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def vector[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], Vector[U], CF] =
    Codec
      .id[List[T], CF](c.format, Schema.binary)
      .mapDecode(ts => DecodeResult.sequence(ts.map(c.decode)).map(_.toVector))(us => us.map(c.encode).toList)
      .schema(c.schema.asIterable[Vector])

  /** Create a codec which requires that a list of low-level values contains a single element. Otherwise a decode failure is returned. The
    * given base codec `c` is used for decoding/encoding.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def listHead[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], U, CF] =
    listBinary(c)
      .mapDecode({
        case Nil     => DecodeResult.Missing
        case List(e) => DecodeResult.Value(e)
        case l       => DecodeResult.Multiple(l)
      })(List(_))
      .schema(c.schema)

  /** Create a codec which requires that a list of low-level values is empty or contains a single element. If it contains multiple elements,
    * a decode failure is returned. The given base codec `c` is used for decoding/encoding.
    *
    * The schema and validator are copied from the base codec.
    */
  implicit def listHeadOption[T, U, CF <: CodecFormat](implicit c: Codec[T, U, CF]): Codec[List[T], Option[U], CF] =
    listBinary(c)
      .mapDecode({
        case Nil     => DecodeResult.Value(None)
        case List(e) => DecodeResult.Value(Some(e))
        case l       => DecodeResult.Multiple(l.map(_.toString))
      })(_.toList)
      .schema(c.schema.asOption)

  /** Create a codec which requires that an optional low-level value is defined. If it is `None`, a decode failure is returned. The given
    * base codec `c` is used for decoding/encoding.
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

  /** Create a codec which decodes/encodes an optional low-level value to an optional high-level value. The given base codec `c` is used for
    * decoding/encoding.
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

  //

  def fromDecodeAndMeta[L, H: Schema, CF <: CodecFormat](cf: CF)(f: L => DecodeResult[H])(g: H => L): Codec[L, H, CF] =
    new Codec[L, H, CF] {
      override def rawDecode(l: L): DecodeResult[H] = f(l)
      override def encode(h: H): L = g(h)
      override def schema: Schema[H] = implicitly[Schema[H]]
      override def format: CF = cf
    }

  def json[T: Schema](_rawDecode: String => DecodeResult[T])(_encode: T => String): JsonCodec[T] = {
    anyString(CodecFormat.Json())(_rawDecode)(_encode)
  }

  private[tapir] def jsonQuery[T](baseCodec: JsonCodec[T]): Codec[List[String], T, CodecFormat.Json] = {
    // we can't implicitly lift the `baseCodec` (String <-> T) to `List[String] <-> T`, as we don't know if `T` is optional
    // (that info is lost due to the indirect codec derivation). In that case, we'd use the implicit `listHeadOption`
    // instead of `listHead`. Hence, handling this case by hand.
    id[List[String], CodecFormat.Json](CodecFormat.Json(), Schema.binary)
      .mapDecode({
        // #2786: if the query parameter is optional, the base json codec will already properly handle the optionality
        // in json codecs (typically used for bodies), an empty input string means that the optional value is absent
        case Nil if baseCodec.schema.isOptional => baseCodec.decode("")
        case Nil                                => DecodeResult.Missing
        case List(e)                            => baseCodec.decode(e)
        case l                                  => DecodeResult.Multiple(l)
      })(e => List(baseCodec.encode(e)))
      .schema(baseCodec.schema)
  }

  def xml[T: Schema](_rawDecode: String => DecodeResult[T])(_encode: T => String): XmlCodec[T] = {
    anyString(CodecFormat.Xml())(_rawDecode)(_encode)
  }

  def anyString[T: Schema, CF <: CodecFormat](
      cf: CF
  )(_rawDecode: String => DecodeResult[T])(_encode: T => String): Codec[String, T, CF] = {
    val isOptional = implicitly[Schema[T]].isOptional
    fromDecodeAndMeta(cf)({ (s: String) =>
      val toDecode = if (isOptional && s == "") "null" else s
      _rawDecode(toDecode)
    })(t => if (isOptional && t == None) "" else _encode(t))
  }

  // header values

  implicit val mediaType: Codec[String, MediaType, CodecFormat.TextPlain] = Codec.string.mapDecode { v =>
    DecodeResult.fromEitherString(v, MediaType.parse(v))
  }(_.toString)

  implicit val etag: Codec[String, ETag, CodecFormat.TextPlain] = Codec.string.mapDecode { v =>
    DecodeResult.fromEitherString(v, ETag.parse(v))
  }(_.toString)

  implicit val range: Codec[String, Range, CodecFormat.TextPlain] = Codec.string.mapDecode { v =>
    DecodeResult.fromEitherString(v, Range.parse(v)).flatMap {
      case Nil     => DecodeResult.Missing
      case List(r) => DecodeResult.Value(r)
      case rs      => DecodeResult.Multiple(rs)
    }
  }(_.toString)

  implicit val contentRange: Codec[String, ContentRange, CodecFormat.TextPlain] = Codec.string.mapDecode { v =>
    DecodeResult.fromEitherString(v, ContentRange.parse(v))
  }(_.toString)

  implicit val cacheDirective: Codec[String, List[CacheDirective], CodecFormat.TextPlain] = Codec.string.mapDecode { v =>
    @tailrec
    def toEitherOrList[T, U](l: List[Either[T, U]], acc: List[U]): Either[T, List[U]] = l match {
      case Nil              => Right(acc.reverse)
      case Left(t) :: _     => Left(t)
      case Right(u) :: tail => toEitherOrList(tail, u :: acc)
    }
    DecodeResult.fromEitherString(v, toEitherOrList(CacheDirective.parse(v), Nil))
  }(_.map(_.toString).mkString(", "))

  private[tapir] def decodeCookie(cookie: String): DecodeResult[List[Cookie]] =
    Cookie.parse(cookie) match {
      case Left(e)  => DecodeResult.Error(cookie, new RuntimeException(e))
      case Right(r) => DecodeResult.Value(r)
    }

  implicit val cookie: Codec[String, List[Cookie], TextPlain] = Codec.string.mapDecode(decodeCookie)(cs => Cookie.toString(cs))
  implicit val cookies: Codec[List[String], List[Cookie], TextPlain] = Codec.list(cookie).map(_.flatten)(List(_))

  private[tapir] def decodeCookieWithMeta(cookie: String): DecodeResult[CookieWithMeta] =
    CookieWithMeta.parse(cookie) match {
      case Left(e)  => DecodeResult.Error(cookie, new RuntimeException(e))
      case Right(r) => DecodeResult.Value(r)
    }

  implicit val cookieWithMeta: Codec[String, CookieWithMeta, TextPlain] = Codec.string.mapDecode(decodeCookieWithMeta)(_.toString)
  implicit val cookiesWithMeta: Codec[List[String], List[CookieWithMeta], TextPlain] = Codec.list(cookieWithMeta)

  // raw tuples

  implicit def tupledWithRaw[L, H, CF <: CodecFormat](implicit codec: Codec[L, H, CF]): Codec[L, (L, H), CF] =
    new Codec[L, (L, H), CF] {
      override def schema: Schema[(L, H)] = codec.schema.map(h => Some(codec.encode(h) -> h))(_._2)
      override def format: CF = codec.format
      override def rawDecode(l: L): DecodeResult[(L, H)] = codec.rawDecode(l).map(l -> _)
      override def encode(h: (L, H)): L = codec.encode(h._2)
    }
}

/** Lower-priority codec implicits, which transform other codecs. For example, when deriving a codec `List[T] <-> Either[A, B]`, given
  * codecs `ca: T <-> A` and `cb: T <-> B`, we want to get `listHead(eitherRight(ca, cb))`, not `eitherRight(listHead(ca), listHead(cb))`
  * (although they would function the same).
  */
trait LowPriorityCodec { this: Codec.type =>

  /** Create a codec which during decoding, first tries to decode values on the right using `c2`. If this fails for any reason, decoding is
    * done using `c1`. Both codecs must have the same low-level values and formats.
    *
    * For a left-biased variant see [[Codec.eitherLeft]]. This right-biased version is the default when using implicit codec resolution.
    *
    * The schema is defined to be an either schema as created by [[Schema.schemaForEither]].
    */
  implicit def eitherRight[L, A, B, CF <: CodecFormat](implicit c1: Codec[L, A, CF], c2: Codec[L, B, CF]): Codec[L, Either[A, B], CF] = {
    Codec
      .id[L, CF](c1.format, Schema.binary) // any schema will do, as we're overriding it later with schemaForEither
      .mapDecode[Either[A, B]] { (l: L) =>
        c2.decode(l) match {
          case _: DecodeResult.Failure => c1.decode(l).map(Left(_))
          case DecodeResult.Value(v)   => DecodeResult.Value(Right(v))
        }
      } {
        case Left(a)  => c1.encode(a)
        case Right(b) => c2.encode(b)
      }
      .schema(Schema.schemaForEither(c1.schema, c2.schema))
  }

  /** Create a codec which during decoding, first tries to decode values on the left using `c1`. If this fails for any reason, decoding is
    * done using `c2`. Both codecs must have the same low-level values and formats.
    *
    * For a right-biased variant see [[Codec.eitherRight]].
    *
    * The schema is defined to be an either schema as created by [[Schema.schemaForEither]].
    */
  def eitherLeft[L, A, B, CF <: CodecFormat](c1: Codec[L, A, CF], c2: Codec[L, B, CF]): Codec[L, Either[A, B], CF] = {
    Codec
      .id[L, CF](c1.format, Schema.binary) // any schema will do, as we're overriding it later with schemaForEither
      .mapDecode[Either[A, B]] { (l: L) =>
        c1.decode(l) match {
          case _: DecodeResult.Failure => c2.decode(l).map(Right(_))
          case DecodeResult.Value(v)   => DecodeResult.Value(Left(v))
        }
      } {
        case Left(a)  => c1.encode(a)
        case Right(b) => c2.encode(b)
      }
      .schema(Schema.schemaForEither(c1.schema, c2.schema))
  }
}

/** Information needed to read a single part of a multipart body: the raw type (`rawBodyType`), and the codec which further decodes it. */
case class PartCodec[R, T](rawBodyType: RawBodyType[R], codec: Codec[List[Part[R]], T, _ <: CodecFormat])
object PartCodec {

  /** Create a part codec which reads the raw part as `R` and later decodes to type `T`. Usage examples:
    * {{{
    * PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8))[String]
    * PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8))[Int]
    * PartCodec(RawBodyType.ByteArrayBody)[Array[Byte]]
    * }}}
    *
    * A codec between the a [[Part]] containing the raw data and the target type `T` must be available in the implicit scope.
    */
  def apply[R](rbt: RawBodyType[R]): PartCodecPartiallyApplied[R] = new PartCodecPartiallyApplied(rbt)

  class PartCodecPartiallyApplied[R](rbt: RawBodyType[R]) {
    def apply[T](implicit c: Codec[List[Part[R]], T, _ <: CodecFormat]): PartCodec[R, T] = PartCodec(rbt, c)
  }
}

/** Information needed to handle a multipart body. Individual parts are decoded as described by [[rawBodyType]], which contains codecs for
  * each part, as well as an optional default codec. The sequence of decoded parts can be further decoded into a high-level `T` type using
  * [[codec]].
  */
case class MultipartCodec[T](rawBodyType: RawBodyType.MultipartBody, codec: Codec[Seq[RawPart], T, CodecFormat.MultipartFormData]) {
  def map[U](mapping: Mapping[T, U]): MultipartCodec[U] = MultipartCodec(rawBodyType, codec.map(mapping))
  def map[U](f: T => U)(g: U => T): MultipartCodec[U] = map(Mapping.from(f)(g))
  def mapDecode[U](f: T => DecodeResult[U])(g: U => T): MultipartCodec[U] = map(Mapping.fromDecode(f)(g))

  def schema(s: Schema[T]): MultipartCodec[T] = copy(codec = codec.schema(s))
  def validate(v: Validator[T]): MultipartCodec[T] = copy(codec = codec.validate(v))
}

object MultipartCodec extends MultipartCodecMacros {
  private val arrayBytePartListCodec = implicitly[Codec[List[Part[Array[Byte]]], List[Part[Array[Byte]]], OctetStream]]

  val Default: MultipartCodec[Seq[Part[Array[Byte]]]] =
    Codec
      .multipart(Map.empty, Some(PartCodec(RawBodyType.ByteArrayBody, arrayBytePartListCodec)))
      // we know that all parts will end up as byte arrays; also, removing/restoring the by-name grouping of parts
      .map(_.values.toSeq.flatMap(_.asInstanceOf[List[Part[Array[Byte]]]]))(l =>
        ListMap(l.groupBy(_.name).toList.map { case (name, parts) => name -> parts.toList }: _*)
      )
}

/** The raw format of the body: what do we need to know, to read it and pass to a codec for further decoding. */
sealed trait RawBodyType[R]
object RawBodyType {
  case class StringBody(charset: Charset) extends RawBodyType[String]

  sealed trait Binary[R] extends RawBodyType[R]
  case object ByteArrayBody extends Binary[Array[Byte]]
  case object ByteBufferBody extends Binary[ByteBuffer]
  case object InputStreamBody extends Binary[InputStream]
  case object FileBody extends Binary[FileRange]
  case object InputStreamRangeBody extends Binary[InputStreamRange]

  case class MultipartBody(partTypes: Map[String, RawBodyType[_]], defaultType: Option[RawBodyType[_]]) extends RawBodyType[Seq[RawPart]] {
    def partType(name: String): Option[RawBodyType[_]] = partTypes.get(name).orElse(defaultType)
  }
}
