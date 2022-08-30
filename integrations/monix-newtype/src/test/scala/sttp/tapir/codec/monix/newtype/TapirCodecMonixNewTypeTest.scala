package sttp.tapir.codec.monix.newtype

import monix.newtypes._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.Value
import sttp.tapir.{Codec, Schema}

class TapirCodecMonixNewTypeTest extends AnyFlatSpec with Matchers {
  import TapirCodecMonixNewTypeTest._

  it should "find schema for newtype which is equal to the schema of its underlying value" in {
    implicitly[Schema[Foo]] shouldBe Schema.schemaForString
  }

  it should "find schema for newsubtype which is equal to the schema of its underlying value" in {
    implicitly[Schema[Bar]] shouldBe Schema.schemaForInt
  }

  "Provided PlainText codec for a newtype" should "equal to the codec of its underlying value" in {
    val newTypeCodec = implicitly[PlainCodec[Foo]]
    newTypeCodec.schema shouldBe Codec.string.schema
    newTypeCodec.format shouldBe Codec.string.format
  }

  it should "correctly deserialize everything it serialize" in {
    val newTypeCodec = implicitly[Codec[String, Foo, TextPlain]]
    val foo = Foo("foo")

    newTypeCodec.decode(newTypeCodec.encode(foo)) shouldBe Value(foo)
  }

  "Provided PlainText codec for a newsubtype" should "equal to the codec of its underlying value" in {
    val newSubTypeCodec = implicitly[PlainCodec[Bar]]
    newSubTypeCodec.schema shouldBe Codec.int.schema
    newSubTypeCodec.format shouldBe Codec.int.format
  }

  it should "correctly deserialize everything it serialize" in {
    val newSubTypeCodec = implicitly[PlainCodec[Bar]]
    val bar = Bar(1)

    newSubTypeCodec.decode(newSubTypeCodec.encode(bar)) shouldBe Value(bar)
  }

}

object TapirCodecMonixNewTypeTest {
  type Foo = Foo.Type
  object Foo extends NewtypeWrapped[String]

  type Bar = Bar.Type
  object Bar extends NewsubtypeWrapped[Int]
}
