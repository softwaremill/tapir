package sttp.tapir.codec.zio.prelude.newtype

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.{Multiple, Value}
import sttp.tapir.{Codec, Schema}

class TapirNewtypeCustomTest extends AnyFlatSpec with Matchers {
  import TapirNewtypeTestFixture.*

  private val PalindromeNewtypeSupport = TapirNewtype[String](Palindrome)

  it should "find schema for NewtypeCustom which is equal to the schema of its underlying value" in {
    import PalindromeNewtypeSupport.tapirSchema
    implicitly[Schema[Palindrome]] shouldBe Schema.schemaForString
  }

  "Provided PlainText codec for a NewtypeCustom" should "equal to the codec of its underlying value" in {
    import PalindromeNewtypeSupport.tapirCodec
    val newTypeCodec = implicitly[PlainCodec[Palindrome]]
    newTypeCodec.schema shouldBe Codec.string.schema
    newTypeCodec.format shouldBe Codec.string.format
  }

  it should "correctly deserialize everything it serializes" in {
    import PalindromeNewtypeSupport.tapirCodec
    val newTypeCodec = implicitly[Codec[String, Palindrome, TextPlain]]
    val kayak = Palindrome("kayak")

    newTypeCodec.decode(newTypeCodec.encode(kayak)) shouldBe Value(kayak)
  }

  it should "not deserialize values that do not pass validation" in {
    import PalindromeNewtypeSupport.tapirCodec
    val newTypeCodec = implicitly[Codec[String, Palindrome, TextPlain]]

    newTypeCodec.decode("asd") shouldBe Multiple(List("asd did not satisfy isPalindrome"))
  }
}
