package tapir.generic

import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema.{SInteger, SObject, SObjectInfo, SString}
import tapir.util.CompileUtil
import tapir.{DecodeResult, GeneralCodec, MediaType}

class FormCodecDerivationTest extends FlatSpec with Matchers {
  it should "generate a codec for a one-arg case class" in {
    // given
    case class Test1(f1: Int)
    val codec = implicitly[GeneralCodec[Test1, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encodeOptional(Test1(10)) shouldBe Some("f1=10")
    codec.decodeOptional(Some("f1=10")) shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a two-arg case class" in {
    // given
    case class Test2(f1: String, f2: Int)
    val codec = implicitly[GeneralCodec[Test2, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encodeOptional(Test2("v1", 10)) shouldBe Some("f1=v1&f2=10")
    codec.encodeOptional(Test2("John Smith Ą", 10)) shouldBe Some("f1=John+Smith+%C4%84&f2=10")

    codec.decodeOptional(Some("f1=v1&f2=10")) shouldBe DecodeResult.Value(Test2("v1", 10))
    codec.decodeOptional(Some("f2=10&f1=v1")) shouldBe DecodeResult.Value(Test2("v1", 10))
    codec.decodeOptional(Some("f1=v1")) shouldBe DecodeResult.Missing
  }

  it should "generate a codec for a three-arg case class" in {
    // given
    case class Test3(f1: String, f2: Int, f3: Boolean)
    val codec = implicitly[GeneralCodec[Test3, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encodeOptional(Test3("v1", 10, f3 = true)) shouldBe Some("f1=v1&f2=10&f3=true")
    codec.decodeOptional(Some("f1=v1&f2=10&f3=true")) shouldBe DecodeResult.Value(Test3("v1", 10, f3 = true))
  }

  it should "generate a codec for a case class with optional parameters" in {
    // given
    case class Test4(f1: Option[String], f2: Int)
    val codec = implicitly[GeneralCodec[Test4, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encodeOptional(Test4(Some("v1"), 10)) shouldBe Some("f1=v1&f2=10")
    codec.encodeOptional(Test4(None, 10)) shouldBe Some("f2=10")

    codec.decodeOptional(Some("f1=v1&f2=10")) shouldBe DecodeResult.Value(Test4(Some("v1"), 10))
    codec.decodeOptional(Some("f2=10")) shouldBe DecodeResult.Value(Test4(None, 10))
  }

  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    val error = CompileUtil.interceptEval("""
        |import tapir._
        |trait NoCodecForThisTrait
        |case class Test5(f1: String, f2: NoCodecForThisTrait)
        |implicitly[GeneralCodec[Test5, MediaType.XWwwFormUrlencoded, String]]
        |""".stripMargin)

    error.message should include("could not find implicit value")
    error.message should include("GeneralPlainCodec[NoCodecForThisTrait]")
  }

  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test6(f1: String, f2: Int)
    val codec = implicitly[GeneralCodec[Test6, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.meta.schema shouldBe SObject(
      SObjectInfo("Test6", "tapir.generic.FormCodecDerivationTest.<local FormCodecDerivationTest>.Test6"),
      List(("f1", SString), ("f2", SInteger)),
      List("f1", "f2")
    )
  }
}
