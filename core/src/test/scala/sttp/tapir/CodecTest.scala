package sttp.tapir

import java.math.{BigDecimal => JBigDecimal}
import java.time._
import java.time.temporal.ChronoUnit
import java.util.{Date, UUID}

import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8.{arbLocalDateJdk8, arbLocalDateTimeJdk8, genZonedDateTimeWithZone}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Assertion, FlatSpec, Matchers}
import org.scalatestplus.scalacheck.Checkers
import sttp.model.Uri
import sttp.model.Uri._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.Value

import scala.concurrent.duration.{Duration => SDuration}
import scala.reflect.ClassTag

class CodecTest extends FlatSpec with Matchers with Checkers {

  implicit val arbitraryUri: Arbitrary[Uri] = Arbitrary(for {
    scheme <- Gen.alphaLowerStr if scheme.nonEmpty
    host <- Gen.identifier.map(_.take(5)) // schemes may not be too long
    port <- Gen.option(Gen.chooseNum(1, Short.MaxValue))
    path <- Gen.listOfN(5, Gen.identifier)
    query <- Gen.mapOf((Gen.identifier, Gen.identifier))
  } yield uri"$scheme://$host:$port/${path.mkString("/")}?$query")

  implicit val arbitraryJBigDecimal: Arbitrary[JBigDecimal] = Arbitrary(
    (implicitly[Arbitrary[BigDecimal]]).arbitrary.map(bd => new JBigDecimal(bd.toString))
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

  implicit val arbitraryLocalTime: Arbitrary[LocalTime] = Arbitrary(Gen.chooseNum(0, 86399999999999L).map(LocalTime.ofNanoOfDay))

  implicit val arbitraryOffsetTime: Arbitrary[OffsetTime] = Arbitrary(arbitraryOffsetDateTime.arbitrary.map(_.toOffsetTime))

  val localDateTimeCodec: Codec[LocalDateTime, TextPlain, String] = implicitly[Codec[LocalDateTime, TextPlain, String]]

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
    checkEncodeDecodeToString[LocalTime]
    checkEncodeDecodeToString[LocalDate]
    checkEncodeDecodeToString[OffsetDateTime]
    checkEncodeDecodeToString[Instant]
    checkEncodeDecodeToString[ZoneOffset]
    checkEncodeDecodeToString[Duration]
    checkEncodeDecodeToString[OffsetTime]
    checkEncodeDecodeToString[SDuration]
  }

  // Because there is no separate standard for local date time, we encode it WITH timezone set to "Z"
  // https://swagger.io/docs/specification/data-models/data-types/#string
  it should "encode LocalDateTime with timezone" in {
    check((ldt: LocalDateTime) => localDateTimeCodec.encode(ldt) == OffsetDateTime.of(ldt, ZoneOffset.UTC).toString)
  }

  it should "decode LocalDateTime from string with timezone" in {
    check((zdt: ZonedDateTime) => localDateTimeCodec.decode(zdt.toOffsetDateTime.toString) == Value(zdt.toLocalDateTime))
  }

  it should "correctly encode and decode ZonedDateTime" in {
    val codec = implicitly[Codec[ZonedDateTime, TextPlain, String]]
    codec.encode(ZonedDateTime.of(LocalDateTime.of(2010, 9, 22, 14, 32, 1), ZoneOffset.ofHours(5))) shouldBe "2010-09-22T14:32:01+05:00"
    codec.encode(ZonedDateTime.of(LocalDateTime.of(2010, 9, 22, 14, 32, 1), ZoneOffset.UTC)) shouldBe "2010-09-22T14:32:01Z"
    check((zdt: ZonedDateTime) => codec.decode(codec.encode(zdt)) == Value(zdt))
  }

  it should "correctly encode and decode OffsetDateTime" in {
    val codec = implicitly[Codec[OffsetDateTime, TextPlain, String]]
    codec.encode(OffsetDateTime.of(LocalDateTime.of(2019, 12, 31, 23, 59, 14), ZoneOffset.ofHours(5))) shouldBe "2019-12-31T23:59:14+05:00"
    codec.encode(OffsetDateTime.of(LocalDateTime.of(2020, 9, 22, 14, 32, 1), ZoneOffset.ofHours(0))) shouldBe "2020-09-22T14:32:01Z"
    check((odt: OffsetDateTime) => codec.decode(codec.encode(odt)) == Value(odt))
  }

  it should "correctly encode and decode example Instants" in {
    val codec = implicitly[Codec[Instant, TextPlain, String]]
    codec.encode(Instant.ofEpochMilli(1583760958000L)) shouldBe "2020-03-09T13:35:58Z"
    codec.decode("2020-02-19T12:35:58Z") shouldBe (Value(Instant.ofEpochMilli(1582115758000L)))
  }

  it should "correctly encode and decode example Durations" in {
    val codec = implicitly[Codec[Duration, TextPlain, String]]
    val start = OffsetDateTime.parse("2020-02-19T12:35:58Z")
    codec.encode(Duration.between(start, start.plusDays(791).plusDays(12).plusSeconds(3))) shouldBe "PT19272H3S"
    codec.decode("PT3H15S") shouldBe Value(Duration.of(10815000, ChronoUnit.MILLIS))
  }

  it should "correctly encode and decode example ZoneOffsets" in {
    val codec = implicitly[Codec[ZoneOffset, TextPlain, String]]
    codec.encode(ZoneOffset.UTC) shouldBe "Z"
    codec.encode(ZoneId.of("Europe/Moscow").getRules.getOffset(Instant.ofEpochMilli(1582115758000L))) shouldBe "+03:00"
    codec.decode("-04:30") shouldBe Value(ZoneOffset.ofHoursMinutes(-4, -30))
  }

  it should "correctly encode and decode example OffsetTime" in {
    val codec = implicitly[Codec[OffsetTime, TextPlain, String]]
    codec.encode(OffsetTime.parse("13:45:30.123456789+02:00")) shouldBe "13:45:30.123456789+02:00"
    codec.decode("12:00-11:30") shouldBe Value(OffsetTime.of(12, 0, 0, 0, ZoneOffset.ofHoursMinutes(-11, -30)))
    codec.decode("06:15Z") shouldBe Value(OffsetTime.of(6, 15, 0, 0, ZoneOffset.UTC))
  }

  it should "correctly encode and decode Date" in {
    val codec = implicitly[Codec[Date, TextPlain, String]]
    check((d: Date) => codec.decode(codec.encode(d)) == Value(d))
  }

  it should "decode LocalDateTime from string without timezone" in {
    check((ldt: LocalDateTime) => localDateTimeCodec.decode(ldt.toString) == Value(ldt))
  }

  def checkEncodeDecodeToString[T: Arbitrary](implicit c: Codec[T, TextPlain, String], ct: ClassTag[T]): Assertion =
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
