package sttp.tapir.generic

import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain

import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}

class ValueClassCodecDerivationTest extends AnyFlatSpec with Matchers {

  it should "derive codec for value classes" in {
    compare(
      derived = Codec.derivedValueClass[StringV],
      toEnc = StringV("text"),
      toDec = "text",
      expected = Codec.string.map(StringV.apply(_))(_.v)
    )
    compare(derived = Codec.derivedValueClass[ByteV], toEnc = ByteV(1), toDec = "1", expected = Codec.byte.map(ByteV.apply(_))(_.v))
    compare(derived = Codec.derivedValueClass[IntV], toEnc = IntV(1), toDec = "1", expected = Codec.int.map(IntV.apply(_))(_.v))
    compare(
      derived = Codec.derivedValueClass[BooleanV],
      toEnc = BooleanV(true),
      toDec = "true",
      expected = Codec.boolean.map(BooleanV.apply(_))(_.v)
    )
    compare(
      derived = Codec.derivedValueClass[OffsetDateTimeV],
      toEnc = OffsetDateTimeV(OffsetDateTime.of(LocalDateTime.of(2019, 12, 31, 23, 59, 14), ZoneOffset.ofHours(5))),
      toDec = "2019-12-31T23:59:14+05:00",
      expected = Codec.offsetDateTime.map(OffsetDateTimeV.apply(_))(_.v)
    )
  }

  def compare[T](derived: Codec[String, T, TextPlain], toEnc: T, toDec: String, expected: Codec[String, T, TextPlain]): Assertion = {
    derived.encode(toEnc) shouldBe expected.encode(toEnc)
    derived.decode(toDec) shouldBe expected.decode(toDec)
  }

}

case class StringV(v: String) extends AnyVal
case class ByteV(v: Byte) extends AnyVal
case class IntV(v: Int) extends AnyVal
case class BooleanV(v: Boolean) extends AnyVal
case class OffsetDateTimeV(v: OffsetDateTime) extends AnyVal
