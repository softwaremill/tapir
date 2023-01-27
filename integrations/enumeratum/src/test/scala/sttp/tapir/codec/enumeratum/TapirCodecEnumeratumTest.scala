package sttp.tapir.codec.enumeratum

import enumeratum.EnumEntry.Snakecase
import enumeratum._
import enumeratum.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.{default, description}
import sttp.tapir.SchemaType.{SInteger, SString}
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._
import sttp.tapir.{DecodeResult, Schema, Validator}

class TapirCodecEnumeratumTest extends AnyFlatSpec with Matchers {

  import TapirCodecEnumeratumTest._

  it should "find schema for enumeratum enum entries and enrich with metadata from annotations" in {
    implicitly[Schema[TestEnumEntry]].schemaType shouldBe SString()
    implicitly[Schema[TestEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestEnumEntry]].description shouldBe Some("test enum entry")
    implicitly[Schema[TestIntEnumEntry]].schemaType shouldBe SInteger()
    implicitly[Schema[TestIntEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestIntEnumEntry]].description shouldBe Some("test int enum entry")
    implicitly[Schema[TestLongEnumEntry]].schemaType shouldBe SInteger()
    implicitly[Schema[TestLongEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestLongEnumEntry]].description shouldBe Some("test long enum entry")
    implicitly[Schema[TestShortEnumEntry]].schemaType shouldBe SInteger()
    implicitly[Schema[TestShortEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestShortEnumEntry]].description shouldBe Some("test short enum entry")
    implicitly[Schema[TestStringEnumEntry]].schemaType shouldBe SString()
    implicitly[Schema[TestStringEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestStringEnumEntry]].description shouldBe Some("test string enum entry")
    implicitly[Schema[TestByteEnumEntry]].schemaType shouldBe SInteger()
    implicitly[Schema[TestByteEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestByteEnumEntry]].description shouldBe Some("test byte enum entry")
    implicitly[Schema[TestCharEnumEntry]].schemaType shouldBe SString()
    implicitly[Schema[TestCharEnumEntry]].isOptional shouldBe false
    implicitly[Schema[TestCharEnumEntry]].description shouldBe Some("test char enum entry")
  }

  it should "find proper validator for enumeratum enum entries" in {
    testEnumValidator(implicitly[Schema[TestEnumEntry]].validator)
    testValueEnumValidator[Int, TestIntEnumEntry, IntEnum[TestIntEnumEntry]](implicitly[Schema[TestIntEnumEntry]].validator)
    testValueEnumValidator[Long, TestLongEnumEntry, LongEnum[TestLongEnumEntry]](implicitly[Schema[TestLongEnumEntry]].validator)
    testValueEnumValidator[Short, TestShortEnumEntry, ShortEnum[TestShortEnumEntry]](implicitly[Schema[TestShortEnumEntry]].validator)
    testValueEnumValidator[String, TestStringEnumEntry, StringEnum[TestStringEnumEntry]](implicitly[Schema[TestStringEnumEntry]].validator)
    testValueEnumValidator[Byte, TestByteEnumEntry, ByteEnum[TestByteEnumEntry]](implicitly[Schema[TestByteEnumEntry]].validator)
    testValueEnumValidator[Char, TestCharEnumEntry, CharEnum[TestCharEnumEntry]](implicitly[Schema[TestCharEnumEntry]].validator)
  }

  private def testEnumValidator[E <: EnumEntry](validator: Validator[E])(implicit `enum`: Enum[E]): Unit = {
    `enum`.values.foreach { v =>
      validator(v) shouldBe Nil
      validator match {
        case Validator.Enumeration(_, Some(encode), name) =>
          encode(v) shouldBe Some(v.entryName)
          name shouldBe Some(SName(fullName(`enum`)))
        case a => fail(s"Expected enum validator with encode function: got $a")
      }
    }
  }

  private def testValueEnumValidator[T, EE <: ValueEnumEntry[T], E <: ValueEnum[T, EE]](validator: Validator[EE])(implicit
      `enum`: E
  ): Unit = {
    `enum`.values.foreach { v =>
      validator(v) shouldBe Nil
      validator match {
        case Validator.Enumeration(_, Some(encode), name) =>
          encode(v) shouldBe Some(v.value)
          name shouldBe Some(SName(fullName(`enum`)))
        case a => fail(s"Expected enum validator with encode function: got $a")
      }
    }
  }

  private def fullName[E](e: E) = s"$className${e.getClass.getSimpleName}".replace("$", ".")

  it should "find correct plain codec for enumeratum enum entries" in {
    testEnumPlainCodec(implicitly[PlainCodec[TestEnumEntry]])
    testValueEnumPlainCodec[Int, TestIntEnumEntry, IntEnum[TestIntEnumEntry]](implicitly[PlainCodec[TestIntEnumEntry]])
    testValueEnumPlainCodec[Long, TestLongEnumEntry, LongEnum[TestLongEnumEntry]](implicitly[PlainCodec[TestLongEnumEntry]])
    testValueEnumPlainCodec[Short, TestShortEnumEntry, ShortEnum[TestShortEnumEntry]](implicitly[PlainCodec[TestShortEnumEntry]])
    testValueEnumPlainCodec[String, TestStringEnumEntry, StringEnum[TestStringEnumEntry]](implicitly[PlainCodec[TestStringEnumEntry]])
    testValueEnumPlainCodec[Byte, TestByteEnumEntry, ByteEnum[TestByteEnumEntry]](implicitly[PlainCodec[TestByteEnumEntry]])
  }

  private def testEnumPlainCodec[E <: EnumEntry](codec: PlainCodec[E])(implicit `enum`: Enum[E]): Unit = {
    `enum`.values.foreach { v =>
      codec.encode(v) shouldBe v.entryName
      codec.decode(v.entryName) shouldBe DecodeResult.Value(v)
    }
  }

  private def testValueEnumPlainCodec[T, EE <: ValueEnumEntry[T], E <: ValueEnum[T, EE]](
      codec: PlainCodec[EE]
  )(implicit `enum`: E): Unit = {
    `enum`.values.foreach { v =>
      codec.encode(v) shouldBe v.value.toString
      codec.decode(v.value.toString) shouldBe DecodeResult.Value(v)
    }
  }

  it should "find schema for enumeratum enum entries and enrich with metadata from default annotations" in {
    implicitly[Schema[TestEnumEntryWithSomeEncodedDefault]].default shouldBe Some(
      (TestEnumEntryWithSomeEncodedDefault.Value2, Some(TestEnumEntryWithSomeEncodedDefault.Value2))
    )
    implicitly[Schema[TestEnumEntryWithNoEncodedDefault]].default shouldBe Some((TestEnumEntryWithNoEncodedDefault.Value2, None))
  }

  it should "create schema with custom discriminator based on enumeratum enum" in {
    // given
    sealed trait OfferType extends EnumEntry with Snakecase
    object OfferType extends Enum[OfferType] {
      case object OfferOne extends OfferType

      override def values: scala.collection.immutable.IndexedSeq[OfferType] = findValues
    }

    sealed trait CreateOfferRequest {
      def `type`: OfferType
    }

    final case class CreateOfferOneRequest(`type`: OfferType) extends CreateOfferRequest

    // then - should compile
    val createOfferRequestSchema: Schema[CreateOfferRequest] = {
      val one = implicitly[Derived[Schema[CreateOfferOneRequest]]].value
      Schema.oneOfUsingField[CreateOfferRequest, OfferType](_.`type`, _.entryName)(OfferType.OfferOne -> one)
    }
  }
}

object TapirCodecEnumeratumTest {
  private val className = this.getClass.getName

  @description("test enum entry")
  sealed trait TestEnumEntry extends EnumEntry

  object TestEnumEntry extends Enum[TestEnumEntry] {
    case object Value1 extends TestEnumEntry
    case object Value2 extends TestEnumEntry
    case object Value3 extends TestEnumEntry

    override def values: scala.collection.immutable.IndexedSeq[TestEnumEntry] = findValues
  }

  @description("test int enum entry")
  sealed abstract class TestIntEnumEntry(val value: Int) extends IntEnumEntry

  object TestIntEnumEntry extends IntEnum[TestIntEnumEntry] {
    case object Value1 extends TestIntEnumEntry(1)
    case object Value2 extends TestIntEnumEntry(2)
    case object Value3 extends TestIntEnumEntry(3)

    override def values: scala.collection.immutable.IndexedSeq[TestIntEnumEntry] = findValues
  }

  @description("test long enum entry")
  sealed abstract class TestLongEnumEntry(val value: Long) extends LongEnumEntry

  object TestLongEnumEntry extends LongEnum[TestLongEnumEntry] {
    case object Value1 extends TestLongEnumEntry(1L)
    case object Value2 extends TestLongEnumEntry(2L)
    case object Value3 extends TestLongEnumEntry(3L)

    override def values: scala.collection.immutable.IndexedSeq[TestLongEnumEntry] = findValues
  }

  @description("test short enum entry")
  sealed abstract class TestShortEnumEntry(val value: Short) extends ShortEnumEntry

  object TestShortEnumEntry extends ShortEnum[TestShortEnumEntry] {
    case object Value1 extends TestShortEnumEntry(1)
    case object Value2 extends TestShortEnumEntry(2)
    case object Value3 extends TestShortEnumEntry(3)

    override def values: scala.collection.immutable.IndexedSeq[TestShortEnumEntry] = findValues
  }

  @description("test string enum entry")
  sealed abstract class TestStringEnumEntry(val value: String) extends StringEnumEntry

  object TestStringEnumEntry extends StringEnum[TestStringEnumEntry] {
    case object Value1 extends TestStringEnumEntry("value-1")
    case object Value2 extends TestStringEnumEntry("value-2")
    case object Value3 extends TestStringEnumEntry("value-3")

    override def values: scala.collection.immutable.IndexedSeq[TestStringEnumEntry] = findValues
  }

  @description("test byte enum entry")
  sealed abstract class TestByteEnumEntry(val value: Byte) extends ByteEnumEntry

  object TestByteEnumEntry extends ByteEnum[TestByteEnumEntry] {
    case object Value1 extends TestByteEnumEntry(1)
    case object Value2 extends TestByteEnumEntry(2)
    case object Value3 extends TestByteEnumEntry(3)

    override def values: scala.collection.immutable.IndexedSeq[TestByteEnumEntry] = findValues
  }

  @description("test char enum entry")
  sealed abstract class TestCharEnumEntry(val value: Char) extends CharEnumEntry

  object TestCharEnumEntry extends CharEnum[TestCharEnumEntry] {
    case object Value1 extends TestCharEnumEntry('1')
    case object Value2 extends TestCharEnumEntry('2')
    case object Value3 extends TestCharEnumEntry('3')

    override def values: scala.collection.immutable.IndexedSeq[TestCharEnumEntry] = findValues
  }

  @default(TestEnumEntryWithSomeEncodedDefault.Value2, encoded = Some(TestEnumEntryWithSomeEncodedDefault.Value2))
  sealed trait TestEnumEntryWithSomeEncodedDefault extends EnumEntry

  object TestEnumEntryWithSomeEncodedDefault extends Enum[TestEnumEntryWithSomeEncodedDefault] {
    case object Value1 extends TestEnumEntryWithSomeEncodedDefault
    case object Value2 extends TestEnumEntryWithSomeEncodedDefault
    case object Value3 extends TestEnumEntryWithSomeEncodedDefault

    override def values: scala.collection.immutable.IndexedSeq[TestEnumEntryWithSomeEncodedDefault] = findValues
  }

  @default(TestEnumEntryWithNoEncodedDefault.Value2, encoded = None)
  sealed trait TestEnumEntryWithNoEncodedDefault extends EnumEntry

  object TestEnumEntryWithNoEncodedDefault extends Enum[TestEnumEntryWithNoEncodedDefault] {
    case object Value1 extends TestEnumEntryWithNoEncodedDefault
    case object Value2 extends TestEnumEntryWithNoEncodedDefault
    case object Value3 extends TestEnumEntryWithNoEncodedDefault

    override def values: scala.collection.immutable.IndexedSeq[TestEnumEntryWithNoEncodedDefault] = findValues
  }
}
