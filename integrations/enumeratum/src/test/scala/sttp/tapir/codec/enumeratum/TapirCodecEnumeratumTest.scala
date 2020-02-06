package sttp.tapir.codec.enumeratum

import enumeratum._
import enumeratum.values._
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.SchemaType.{SInteger, SString}
import sttp.tapir.{DecodeResult, Schema, Validator}

class TapirCodecEnumeratumTest extends FlatSpec with Matchers {
  import TapirCodecEnumeratumTest._

  it should "find schema for enumeratum enum entries" in {
    implicitly[Schema[TestEnumEntry]].schemaType shouldBe SString
    implicitly[Schema[TestEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestIntEnumEntry]].schemaType shouldBe SInteger
    implicitly[Schema[TestIntEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestLongEnumEntry]].schemaType shouldBe SInteger
    implicitly[Schema[TestLongEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestShortEnumEntry]].schemaType shouldBe SInteger
    implicitly[Schema[TestShortEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestStringEnumEntry]].schemaType shouldBe SString
    implicitly[Schema[TestStringEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestByteEnumEntry]].schemaType shouldBe SInteger
    implicitly[Schema[TestByteEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestCharEnumEntry]].schemaType shouldBe SString
    implicitly[Schema[TestCharEnumEntry]].isOptional shouldBe false
  }

  it should "find proper validator for enumeratum enum entries" in {

    testEnumValidator(implicitly[Validator[TestEnumEntry]])
    testValueEnumValidator[Int, TestIntEnumEntry, IntEnum[TestIntEnumEntry]](implicitly[Validator[TestIntEnumEntry]])
    testValueEnumValidator[Long, TestLongEnumEntry, LongEnum[TestLongEnumEntry]](implicitly[Validator[TestLongEnumEntry]])
    testValueEnumValidator[Short, TestShortEnumEntry, ShortEnum[TestShortEnumEntry]](implicitly[Validator[TestShortEnumEntry]])
    testValueEnumValidator[String, TestStringEnumEntry, StringEnum[TestStringEnumEntry]](implicitly[Validator[TestStringEnumEntry]])
    testValueEnumValidator[Byte, TestByteEnumEntry, ByteEnum[TestByteEnumEntry]](implicitly[Validator[TestByteEnumEntry]])
    testValueEnumValidator[Char, TestCharEnumEntry, CharEnum[TestCharEnumEntry]](implicitly[Validator[TestCharEnumEntry]])
  }

  private def testEnumValidator[E <: EnumEntry](validator: Validator[E])(implicit enum: Enum[E]) = {
    enum.values.foreach { v =>
      validator.validate(v) shouldBe Nil
      validator match {
        case Validator.Enum(_, Some(encode)) => encode(v) shouldBe Some(v.entryName)
        case a => fail(s"Expected enum validator with encode function: got $a")
      }
    }
  }

  private def testValueEnumValidator[T, EE <: ValueEnumEntry[T], E <: ValueEnum[T, EE]](validator: Validator[EE])(implicit enum: E) = {
    enum.values.foreach { v =>
      validator.validate(v) shouldBe Nil
      validator match {
        case Validator.Enum(_, Some(encode)) => encode(v) shouldBe Some(v.value)
        case a => fail(s"Expected enum validator with encode function: got $a")
      }
    }
  }

  it should "find correct plain codec for enumeratum enum entries" in {
    testEnumPlainCodec(implicitly[PlainCodec[TestEnumEntry]])
    testValueEnumPlainCodec[Int, TestIntEnumEntry, IntEnum[TestIntEnumEntry]](implicitly[PlainCodec[TestIntEnumEntry]])
    testValueEnumPlainCodec[Long, TestLongEnumEntry, LongEnum[TestLongEnumEntry]](implicitly[PlainCodec[TestLongEnumEntry]])
    testValueEnumPlainCodec[Short, TestShortEnumEntry, ShortEnum[TestShortEnumEntry]](implicitly[PlainCodec[TestShortEnumEntry]])
    testValueEnumPlainCodec[String, TestStringEnumEntry, StringEnum[TestStringEnumEntry]](implicitly[PlainCodec[TestStringEnumEntry]])
    testValueEnumPlainCodec[Byte, TestByteEnumEntry, ByteEnum[TestByteEnumEntry]](implicitly[PlainCodec[TestByteEnumEntry]])
  }

  private def testEnumPlainCodec[E <: EnumEntry](codec: PlainCodec[E])(implicit enum: Enum[E]) = {
    enum.values.foreach { v =>
      codec.encode(v) shouldBe v.entryName
      codec.decode(v.entryName) shouldBe DecodeResult.Value(v)
    }
  }

  private def testValueEnumPlainCodec[T, EE <: ValueEnumEntry[T], E <: ValueEnum[T, EE]](codec: PlainCodec[EE])(implicit enum: E) = {
    enum.values.foreach { v =>
      codec.encode(v) shouldBe v.value.toString
      codec.decode(v.value.toString) shouldBe DecodeResult.Value(v)
    }
  }

}

object TapirCodecEnumeratumTest {
  sealed trait TestEnumEntry extends EnumEntry

  object TestEnumEntry extends Enum[TestEnumEntry] {
    case object Value1 extends TestEnumEntry
    case object Value2 extends TestEnumEntry
    case object Value3 extends TestEnumEntry

    override def values = findValues
  }

  sealed abstract class TestIntEnumEntry(val value: Int) extends IntEnumEntry

  object TestIntEnumEntry extends IntEnum[TestIntEnumEntry] {
    case object Value1 extends TestIntEnumEntry(1)
    case object Value2 extends TestIntEnumEntry(2)
    case object Value3 extends TestIntEnumEntry(3)

    override def values = findValues
  }

  sealed abstract class TestLongEnumEntry(val value: Long) extends LongEnumEntry

  object TestLongEnumEntry extends LongEnum[TestLongEnumEntry] {
    case object Value1 extends TestLongEnumEntry(1L)
    case object Value2 extends TestLongEnumEntry(2L)
    case object Value3 extends TestLongEnumEntry(3L)

    override def values = findValues
  }

  sealed abstract class TestShortEnumEntry(val value: Short) extends ShortEnumEntry

  object TestShortEnumEntry extends ShortEnum[TestShortEnumEntry] {
    case object Value1 extends TestShortEnumEntry(1)
    case object Value2 extends TestShortEnumEntry(2)
    case object Value3 extends TestShortEnumEntry(3)

    override def values = findValues
  }

  sealed abstract class TestStringEnumEntry(val value: String) extends StringEnumEntry

  object TestStringEnumEntry extends StringEnum[TestStringEnumEntry] {
    case object Value1 extends TestStringEnumEntry("value-1")
    case object Value2 extends TestStringEnumEntry("value-2")
    case object Value3 extends TestStringEnumEntry("value-3")

    override def values = findValues
  }

  sealed abstract class TestByteEnumEntry(val value: Byte) extends ByteEnumEntry

  object TestByteEnumEntry extends ByteEnum[TestByteEnumEntry] {
    case object Value1 extends TestByteEnumEntry(1)
    case object Value2 extends TestByteEnumEntry(2)
    case object Value3 extends TestByteEnumEntry(3)

    override def values = findValues
  }

  sealed abstract class TestCharEnumEntry(val value: Char) extends CharEnumEntry

  object TestCharEnumEntry extends CharEnum[TestCharEnumEntry] {
    case object Value1 extends TestCharEnumEntry('1')
    case object Value2 extends TestCharEnumEntry('2')
    case object Value3 extends TestCharEnumEntry('3')

    override def values = findValues
  }
}
