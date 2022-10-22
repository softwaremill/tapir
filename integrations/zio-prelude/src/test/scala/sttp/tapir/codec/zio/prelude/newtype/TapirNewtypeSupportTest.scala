package sttp.tapir.codec.zio.prelude.newtype

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.{Multiple, Value}
import sttp.tapir.{Codec, Schema}

class TapirNewtypeSupportTest extends AnyFlatSpec with Matchers {
  import TapirNewtypeSupportTestFixture._

  it should "find schema for Newtype which is equal to the schema of its underlying value" in {
    import StringNewtypeWithMixin.tapirSchema
    implicitly[Schema[StringNewtypeWithMixin]] shouldBe Schema.schemaForString
  }

  it should "find schema for Subtype which is equal to the schema of its underlying value" in {
    import IntSubtypeWithMixin.tapirSchema
    implicitly[Schema[IntSubtypeWithMixin]] shouldBe Schema.schemaForInt
  }

  "Provided PlainText codec for a Newtype" should "equal to the codec of its underlying value" in {
    import StringNewtypeWithMixin.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[StringNewtypeWithMixin]]
    newTypeCodec.schema shouldBe Codec.string.schema
    newTypeCodec.format shouldBe Codec.string.format
  }

  it should "correctly deserialize everything it serializes" in {
    import StringNewtypeWithMixin.tapirCodec
    val newTypeCodec = implicitly[Codec[String, StringNewtypeWithMixin, TextPlain]]
    val foo = StringNewtypeWithMixin("foobaz")

    newTypeCodec.decode(newTypeCodec.encode(foo)) shouldBe Value(foo)
  }

  it should "not deserialize values that do not pass validation" in {
    import StringNewtypeWithMixin.tapirCodec
    val newTypeCodec = implicitly[Codec[String, StringNewtypeWithMixin, TextPlain]]

    newTypeCodec.decode("asd") shouldBe Multiple(List("asd did not satisfy startsWith(foo)", "asd did not satisfy startsWith(baz)"))
  }

  "Provided PlainText codec for a Subtype" should "equal to the codec of its underlying value" in {
    import IntSubtypeWithMixin.tapirCodec
    val newSubTypeCodec = implicitly[PlainCodec[IntSubtypeWithMixin]]
    newSubTypeCodec.schema shouldBe Codec.int.schema
    newSubTypeCodec.format shouldBe Codec.int.format
  }

  it should "correctly deserialize everything it serializes" in {
    import IntSubtypeWithMixin.tapirCodec
    val newSubTypeCodec = implicitly[PlainCodec[IntSubtypeWithMixin]]
    val bar = IntSubtypeWithMixin(6)

    newSubTypeCodec.decode(newSubTypeCodec.encode(bar)) shouldBe Value(bar)
  }

  it should "not deserialize values that do not pass validation" in {
    import IntSubtypeWithMixin.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[IntSubtypeWithMixin]]

    newTypeCodec.decode("3") shouldBe Multiple(List("3 did not satisfy divisibleBy(2)", "3 did not satisfy greaterThan(5)"))
  }

}
