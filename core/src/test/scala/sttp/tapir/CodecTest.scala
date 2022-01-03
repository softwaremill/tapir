package sttp.tapir

import java.math.{BigDecimal => JBigDecimal}
import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.{Date, UUID}
import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8.{arbLocalDateJdk8, arbLocalDateTimeJdk8, genZonedDateTimeWithZone}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Assertion
import org.scalatestplus.scalacheck.Checkers
import sttp.model.Uri
import sttp.model.Uri._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.Value

import scala.concurrent.duration.{Duration => SDuration}
import scala.reflect.ClassTag
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CodecTest extends AnyFlatSpec with Matchers with Checkers {

  implicit val arbitraryUri: Arbitrary[Uri] = Arbitrary(for {
    scheme <- Gen.alphaLowerStr if scheme.nonEmpty
    host <- Gen.identifier.map(_.take(5)) // schemes may not be too long
    port <- Gen.option[Int](Gen.chooseNum(1, Short.MaxValue))
    path <- Gen.listOfN(5, Gen.identifier)
    query <- Gen.mapOf(Gen.zip(Gen.identifier, Gen.identifier))
  } yield uri"$scheme://$host:$port/${path.mkString("/")}?$query")

  implicit val arbitraryJBigDecimal: Arbitrary[JBigDecimal] = Arbitrary(
    implicitly[Arbitrary[BigDecimal]].arbitrary.map(bd => new JBigDecimal(bd.toString))
  )

  implicit val arbitraryZonedDateTime: Arbitrary[ZonedDateTime] = Arbitrary(
    genZonedDateTimeWithZone(Some(ZoneOffset.ofHoursMinutes(3, 30)))
  )

  implicit val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] =
    Arbitrary(arbitraryZonedDateTime.arbitrary.map(_.toOffsetDateTime))

  implicit val arbitraryInstant: Arbitrary[Instant] = Arbitrary(Gen.posNum[Long].map(Instant.ofEpochMilli))

  implicit val arbitraryDuration: Arbitrary[Duration] =
    Arbitrary(for {
      instant1 <- arbitraryInstant.arbitrary
      instant2 <- arbitraryInstant.arbitrary.suchThat(_.isAfter(instant1))
    } yield Duration.between(instant1, instant2))

  implicit val arbitraryScalaDuration: Arbitrary[SDuration] =
    Arbitrary(arbitraryDuration.arbitrary.map(d => SDuration.fromNanos(d.toNanos): SDuration))

  implicit val arbitraryZoneOffset: Arbitrary[ZoneOffset] = Arbitrary(
    arbitraryOffsetDateTime.arbitrary.map(_.getOffset)
  )

  implicit val arbitraryLocalTime: Arbitrary[LocalTime] = Arbitrary(Gen.chooseNum[Long](0, 86399999999999L).map(LocalTime.ofNanoOfDay))

  implicit val arbitraryOffsetTime: Arbitrary[OffsetTime] = Arbitrary(arbitraryOffsetDateTime.arbitrary.map(_.toOffsetTime))

  val localDateTimeCodec: Codec[String, LocalDateTime, TextPlain] = implicitly[Codec[String, LocalDateTime, TextPlain]]

  it should "encode simple types using .toString" in {
    checkEncodeDecodeToString[String]
    checkEncodeDecodeToString[Byte]
    checkEncodeDecodeToString[Int]
    checkEncodeDecodeToString[Long]
    checkEncodeDecodeToString[Float]
    checkEncodeDecodeToString[Double]
    checkEncodeDecodeToString[Boolean]
    checkEncodeDecodeToString[UUID]
    checkEncodeDecodeToString[Uri]
    checkEncodeDecodeToString[BigDecimal]
    checkEncodeDecodeToString[JBigDecimal]
    checkEncodeDecodeToString[Duration]
    checkEncodeDecodeToString[SDuration]
  }

  // Because there is no separate standard for local date time, we encode it WITH timezone set to "Z"
  // https://swagger.io/docs/specification/data-models/data-types/#string
  it should "encode LocalDateTime with timezone" in {
    check((ldt: LocalDateTime) => localDateTimeCodec.encode(ldt) == OffsetDateTime.of(ldt, ZoneOffset.UTC).toString)
  }

  it should "decode LocalDateTime from string with timezone" in {
    check((zdt: ZonedDateTime) =>
      localDateTimeCodec.decode(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(zdt)) == Value(zdt.toLocalDateTime)
    )
  }

  it should "correctly encode and decode ZonedDateTime" in {
    val codec = implicitly[Codec[String, ZonedDateTime, TextPlain]]
    codec.encode(ZonedDateTime.of(LocalDateTime.of(2010, 9, 22, 14, 32, 1), ZoneOffset.ofHours(5))) shouldBe "2010-09-22T14:32:01+05:00"
    codec.encode(ZonedDateTime.of(LocalDateTime.of(2010, 9, 22, 14, 32, 1), ZoneOffset.UTC)) shouldBe "2010-09-22T14:32:01Z"
    check { (zdt: ZonedDateTime) =>
      val encoded = codec.encode(zdt)
      codec.decode(encoded) == Value(zdt) && ZonedDateTime.parse(encoded) == zdt
    }
  }

  it should "correctly encode and decode OffsetDateTime" in {
    val codec = implicitly[Codec[String, OffsetDateTime, TextPlain]]
    codec.encode(OffsetDateTime.of(LocalDateTime.of(2019, 12, 31, 23, 59, 14), ZoneOffset.ofHours(5))) shouldBe "2019-12-31T23:59:14+05:00"
    codec.encode(OffsetDateTime.of(LocalDateTime.of(2020, 9, 22, 14, 32, 1), ZoneOffset.ofHours(0))) shouldBe "2020-09-22T14:32:01Z"
    check { (odt: OffsetDateTime) =>
      val encoded = codec.encode(odt)
      codec.decode(encoded) == Value(odt) && OffsetDateTime.parse(encoded) == odt
    }
  }

  it should "correctly encode and decode Instant" in {
    val codec = implicitly[Codec[String, Instant, TextPlain]]
    codec.encode(Instant.ofEpochMilli(1583760958000L)) shouldBe "2020-03-09T13:35:58Z"
    codec.encode(Instant.EPOCH) shouldBe "1970-01-01T00:00:00Z"
    codec.decode("2020-02-19T12:35:58Z") shouldBe Value(Instant.ofEpochMilli(1582115758000L))
    check { (i: Instant) =>
      val encoded = codec.encode(i)
      codec.decode(encoded) == Value(i) && Instant.parse(encoded) == i
    }
  }

  it should "correctly encode and decode example Durations" in {
    val codec = implicitly[Codec[String, Duration, TextPlain]]
    val start = OffsetDateTime.parse("2020-02-19T12:35:58Z")
    codec.encode(Duration.between(start, start.plusDays(791).plusDays(12).plusSeconds(3))) shouldBe "PT19272H3S"
    codec.decode("PT3H15S") shouldBe Value(Duration.of(10815000, ChronoUnit.MILLIS))
    check { (d: Duration) =>
      val encoded = codec.encode(d)
      codec.decode(encoded) == Value(d) && Duration.parse(encoded) == d
    }
  }

  it should "correctly encode and decode example ZoneOffsets" in {
    val codec = implicitly[Codec[String, ZoneOffset, TextPlain]]
    codec.encode(ZoneOffset.UTC) shouldBe "Z"
    codec.encode(ZoneId.of("Europe/Moscow").getRules.getOffset(Instant.ofEpochMilli(1582115758000L))) shouldBe "+03:00"
    codec.decode("-04:30") shouldBe Value(ZoneOffset.ofHoursMinutes(-4, -30))
  }

  it should "correctly encode and decode example OffsetTime" in {
    val codec = implicitly[Codec[String, OffsetTime, TextPlain]]
    codec.encode(OffsetTime.parse("13:45:30.123456789+02:00")) shouldBe "13:45:30.123456789+02:00"
    codec.decode("12:00-11:30") shouldBe Value(OffsetTime.of(12, 0, 0, 0, ZoneOffset.ofHoursMinutes(-11, -30)))
    codec.decode("06:15Z") shouldBe Value(OffsetTime.of(6, 15, 0, 0, ZoneOffset.UTC))
    check { (ot: OffsetTime) =>
      val encoded = codec.encode(ot)
      codec.decode(encoded) == Value(ot) && OffsetTime.parse(encoded) == ot
    }
  }

  it should "decode LocalDateTime from string without timezone" in {
    check((ldt: LocalDateTime) => localDateTimeCodec.decode(ldt.toString) == Value(ldt))
  }

  it should "correctly encode and decode LocalTime" in {
    val codec = implicitly[Codec[String, LocalTime, TextPlain]]
    codec.encode(LocalTime.of(22, 59, 31, 3)) shouldBe "22:59:31.000000003"
    codec.encode(LocalTime.of(13, 30)) shouldBe "13:30:00"
    codec.decode("22:59:31.000000003") shouldBe Value(LocalTime.of(22, 59, 31, 3))
    check { (lt: LocalTime) =>
      val encoded = codec.encode(lt)
      codec.decode(encoded) == Value(lt) && LocalTime.parse(encoded) == lt
    }
  }

  it should "correctly encode and decode LocalDate" in {
    val codec = implicitly[Codec[String, LocalDate, TextPlain]]
    codec.encode(LocalDate.of(2019, 12, 31)) shouldBe "2019-12-31"
    codec.encode(LocalDate.of(2020, 9, 22)) shouldBe "2020-09-22"
    check { (ld: LocalDate) =>
      val encoded = codec.encode(ld)
      codec.decode(encoded) == Value(ld) && LocalDate.parse(encoded) == ld
    }
  }

  it should "use default, when available" in {
    val codec = implicitly[Codec[List[String], String, TextPlain]]
    val codecWithDefault = codec.schema(_.default("X"))

    codec.decode(Nil) shouldBe DecodeResult.Missing
    codecWithDefault.decode(Nil) shouldBe DecodeResult.Value("X")

    codec.decode(List("y")) shouldBe DecodeResult.Value("y")
    codec.decode(List("y", "z")) shouldBe DecodeResult.Multiple(List("y", "z"))
  }

  it should "correctly encode and decode Date" in {
    // while Date works on Scala.js, ScalaCheck tests involving Date use java.util.Calendar which doesn't - hence here a normal test
    val codec = implicitly[Codec[String, Date, TextPlain]]
    val d = Date.from(Instant.ofEpochMilli(1619518169000L))
    val encoded = codec.encode(d)
    codec.decode(encoded) == Value(d) && Date.from(Instant.parse(encoded)) == d
  }

  def checkEncodeDecodeToString[T: Arbitrary](implicit c: Codec[String, T, TextPlain], ct: ClassTag[T]): Assertion =
    withClue(s"Test for ${ct.runtimeClass.getName}") {
      check((a: T) => {
        val decodedAndReEncoded = c.decode(c.encode(a)) match {
          case Value(v)   => c.encode(v)
          case unexpected => fail(s"Value $a got decoded to unexpected $unexpected")
        }
        decodedAndReEncoded == a.toString
      })
    }
}
