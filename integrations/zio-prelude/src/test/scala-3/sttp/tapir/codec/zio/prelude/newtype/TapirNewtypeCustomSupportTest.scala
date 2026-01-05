package sttp.tapir.codec.zio.prelude.newtype

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.{Multiple, Value}
import sttp.tapir.{Codec, Schema}

class TapirNewtypeCustomSupportTest extends AnyFlatSpec with Matchers {
  import TapirNewtypeSupportTestFixture.*

  it should "find schema for NewtypeCustom which is equal to the schema of its underlying value" in {
    import StringNewtypeWithMixin.tapirSchema
    implicitly[Schema[PalindromeWithMixin]] shouldBe Schema.schemaForString
  }

  "Provided PlainText codec for a NewtypeCustom" should "equal to the codec of its underlying value" in {
    import StringNewtypeWithMixin.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[PalindromeWithMixin]]
    newTypeCodec.schema shouldBe Codec.string.schema
    newTypeCodec.format shouldBe Codec.string.format
  }

  it should "correctly deserialize everything it serializes" in {
    import StringNewtypeWithMixin.tapirCodec
    val newTypeCodec = implicitly[Codec[String, PalindromeWithMixin, TextPlain]]
    val foo = PalindromeWithMixin("kayak")

    newTypeCodec.decode(newTypeCodec.encode(foo)) shouldBe Value(foo)
  }

  it should "not deserialize values that do not pass validation" in {
    import StringNewtypeWithMixin.tapirCodec
    val newTypeCodec = implicitly[Codec[String, PalindromeWithMixin, TextPlain]]

    newTypeCodec.decode("palindrome") shouldBe Multiple(List("palindrome did not satisfy isPalindrome"))
  }
}
