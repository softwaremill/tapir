package sttp.tapir.codec.zio.prelude.newtype

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.{Multiple, Value}
import sttp.tapir.{Codec, Schema}
import zio.prelude.Assertion.{divisibleBy, endsWith, greaterThan, startsWith}
import zio.prelude.{Newtype, Subtype}

class TapirNewtypeSupportTest extends AnyFlatSpec with Matchers {
  import TapirNewtypeSupportTest._

  it should "find schema for Newtype which is equal to the schema of its underlying value" in {
    import StartsWithFooEndsWithBaz.tapirSchema
    implicitly[Schema[StartsWithFooEndsWithBaz]] shouldBe Schema.schemaForString
  }

  it should "find schema for Subtype which is equal to the schema of its underlying value" in {
    import EvenGreaterThanFive.tapirSchema
    implicitly[Schema[EvenGreaterThanFive]] shouldBe Schema.schemaForInt
  }

  "Provided PlainText codec for a Newtype" should "equal to the codec of its underlying value" in {
    import StartsWithFooEndsWithBaz.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[StartsWithFooEndsWithBaz]]
    newTypeCodec.schema shouldBe Codec.string.schema
    newTypeCodec.format shouldBe Codec.string.format
  }

  it should "correctly deserialize everything it serializes" in {
    import StartsWithFooEndsWithBaz.tapirCodec
    val newTypeCodec = implicitly[Codec[String, StartsWithFooEndsWithBaz, TextPlain]]
    val foo = StartsWithFooEndsWithBaz("foobaz")

    newTypeCodec.decode(newTypeCodec.encode(foo)) shouldBe Value(foo)
  }

  it should "not deserialize values that do not pass validation" in {
    import StartsWithFooEndsWithBaz.tapirCodec
    val newTypeCodec = implicitly[Codec[String, StartsWithFooEndsWithBaz, TextPlain]]

    newTypeCodec.decode("asd") shouldBe Multiple(List("asd did not satisfy startsWith(foo)", "asd did not satisfy startsWith(baz)"))
  }

  "Provided PlainText codec for a Subtype" should "equal to the codec of its underlying value" in {
    import EvenGreaterThanFive.tapirCodec
    val newSubTypeCodec = implicitly[PlainCodec[EvenGreaterThanFive]]
    newSubTypeCodec.schema shouldBe Codec.int.schema
    newSubTypeCodec.format shouldBe Codec.int.format
  }

  it should "correctly deserialize everything it serializes" in {
    import EvenGreaterThanFive.tapirCodec
    val newSubTypeCodec = implicitly[PlainCodec[EvenGreaterThanFive]]
    val bar = EvenGreaterThanFive(6)

    newSubTypeCodec.decode(newSubTypeCodec.encode(bar)) shouldBe Value(bar)
  }

  it should "not deserialize values that do not pass validation" in {
    import EvenGreaterThanFive.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[EvenGreaterThanFive]]

    newTypeCodec.decode("3") shouldBe Multiple(List("3 did not satisfy divisibleBy(2)", "3 did not satisfy greaterThan(5)"))
  }

}

object TapirNewtypeSupportTest {
  object StartsWithFooEndsWithBaz extends Newtype[String] with TapirNewtypeSupport[String] {
    override def assertion = assert(startsWith("foo") && endsWith("baz"))
  }
  type StartsWithFooEndsWithBaz = StartsWithFooEndsWithBaz.Type

  object EvenGreaterThanFive extends Subtype[Int] with TapirNewtypeSupport[Int] {
    override def assertion = assert(divisibleBy(2) && greaterThan(5))
  }
  type EvenGreaterThanFive = EvenGreaterThanFive.Type
}
