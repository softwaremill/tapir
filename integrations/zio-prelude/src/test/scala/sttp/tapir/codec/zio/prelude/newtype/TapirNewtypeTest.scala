package sttp.tapir.codec.zio.prelude.newtype

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.{Multiple, Value}
import sttp.tapir.{Codec, Schema}

class TapirNewtypeTest extends AnyFlatSpec with Matchers {
  import TapirNewtypeTestFixture._

  private val StringNewtypeSupport = TapirNewtype[String](StringNewtype)
  private val IntSubtypeSupport = TapirNewtype[Int](IntSubtype)

  it should "find schema for Newtype which is equal to the schema of its underlying value" in {
    import StringNewtypeSupport.tapirSchema
    implicitly[Schema[StringNewtype]] shouldBe Schema.schemaForString
  }

  it should "find schema for Subtype which is equal to the schema of its underlying value" in {
    import IntSubtypeSupport.tapirSchema
    implicitly[Schema[IntSubtype]] shouldBe Schema.schemaForInt
  }

  "Provided PlainText codec for a Newtype" should "equal to the codec of its underlying value" in {
    import StringNewtypeSupport.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[StringNewtype]]
    newTypeCodec.schema shouldBe Codec.string.schema
    newTypeCodec.format shouldBe Codec.string.format
  }

  it should "correctly deserialize everything it serializes" in {
    import StringNewtypeSupport.tapirCodec
    val newTypeCodec = implicitly[Codec[String, StringNewtype, TextPlain]]
    val foo = StringNewtype("foobaz")

    newTypeCodec.decode(newTypeCodec.encode(foo)) shouldBe Value(foo)
  }

  it should "not deserialize values that do not pass validation" in {
    import StringNewtypeSupport.tapirCodec
    val newTypeCodec = implicitly[Codec[String, StringNewtype, TextPlain]]

    newTypeCodec.decode("asd") shouldBe Multiple(List("asd did not satisfy startsWith(foo)", "asd did not satisfy endsWith(baz)"))
  }

  "Provided PlainText codec for a Subtype" should "equal to the codec of its underlying value" in {
    import IntSubtypeSupport.tapirCodec
    val newSubTypeCodec = implicitly[PlainCodec[IntSubtype]]
    newSubTypeCodec.schema shouldBe Codec.int.schema
    newSubTypeCodec.format shouldBe Codec.int.format
  }

  it should "correctly deserialize everything it serializes" in {
    import IntSubtypeSupport.tapirCodec
    val newSubTypeCodec = implicitly[PlainCodec[IntSubtype]]
    val bar = IntSubtype(6)

    newSubTypeCodec.decode(newSubTypeCodec.encode(bar)) shouldBe Value(bar)
  }

  it should "not deserialize values that do not pass validation" in {
    import IntSubtypeSupport.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[IntSubtype]]

    newTypeCodec.decode("3") shouldBe Multiple(List("3 did not satisfy divisibleBy(2)", "3 did not satisfy greaterThan(5)"))
  }

}
