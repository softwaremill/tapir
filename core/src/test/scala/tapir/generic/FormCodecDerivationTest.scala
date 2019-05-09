package tapir.generic

import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema.{SInteger, SProduct, SObjectInfo, SString}
import tapir.util.CompileUtil
import tapir.{Codec, DecodeResult, MediaType}

class FormCodecDerivationTest extends FlatSpec with Matchers {
  it should "generate a codec for a one-arg case class" in {
    // given
    case class Test1(f1: Int)
    val codec = implicitly[Codec[Test1, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encode(Test1(10)) shouldBe "f1=10"
    codec.decode("f1=10") shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a two-arg case class" in {
    // given
    case class Test2(f1: String, f2: Int)
    val codec = implicitly[Codec[Test2, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encode(Test2("v1", 10)) shouldBe "f1=v1&f2=10"
    codec.encode(Test2("John Smith Ą", 10)) shouldBe "f1=John+Smith+%C4%84&f2=10"

    codec.decode("f1=v1&f2=10") shouldBe DecodeResult.Value(Test2("v1", 10))
    codec.decode("f2=10&f1=v1") shouldBe DecodeResult.Value(Test2("v1", 10))
    codec.decode("f1=v1") shouldBe DecodeResult.Missing
  }

  it should "generate a codec for a three-arg case class" in {
    // given
    case class Test3(f1: String, f2: Int, f3: Boolean)
    val codec = implicitly[Codec[Test3, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encode(Test3("v1", 10, f3 = true)) shouldBe "f1=v1&f2=10&f3=true"
    codec.decode("f1=v1&f2=10&f3=true") shouldBe DecodeResult.Value(Test3("v1", 10, f3 = true))
  }

  it should "generate a codec for a case class with optional parameters" in {
    // given
    case class Test4(f1: Option[String], f2: Int)
    val codec = implicitly[Codec[Test4, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encode(Test4(Some("v1"), 10)) shouldBe "f1=v1&f2=10"
    codec.encode(Test4(None, 10)) shouldBe "f2=10"

    codec.decode("f1=v1&f2=10") shouldBe DecodeResult.Value(Test4(Some("v1"), 10))
    codec.decode("f2=10") shouldBe DecodeResult.Value(Test4(None, 10))
  }

  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    val error = CompileUtil.interceptEval("""
        |import tapir._
        |trait NoCodecForThisTrait
        |case class Test5(f1: String, f2: NoCodecForThisTrait)
        |implicitly[Codec[Test5, MediaType.XWwwFormUrlencoded, String]]
        |""".stripMargin)

    error.message should include("Cannot find a codec for type: NoCodecForThisTrait")
  }

  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test6(f1: String, f2: Int)
    val codec = implicitly[Codec[Test6, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.meta.schema shouldBe SProduct(
      SObjectInfo("tapir.generic.FormCodecDerivationTest.<local FormCodecDerivationTest>.Test6"),
      List(("f1", SString), ("f2", SInteger)),
      List("f1", "f2")
    )
  }

  it should "generate a codec for a one-arg case class using snake-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
    val codec = implicitly[Codec[CaseClassWithComplicatedName, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encode(CaseClassWithComplicatedName(10)) shouldBe "complicated_name=10"
    codec.decode("complicated_name=10") shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class using kebab-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withKebabCaseMemberNames
    val codec = implicitly[Codec[CaseClassWithComplicatedName, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encode(CaseClassWithComplicatedName(10)) shouldBe "complicated-name=10"
    codec.decode("complicated-name=10") shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class with list" in {
    // given
    case class Test1(f1: List[Int])
    val codec = implicitly[Codec[Test1, MediaType.XWwwFormUrlencoded, String]]

    // when
    codec.encode(Test1(Nil)) shouldBe ""
    codec.encode(Test1(List(10))) shouldBe "f1=10"
    codec.encode(Test1(List(10, 12))) shouldBe "f1=10&f1=12"

    codec.decode("") shouldBe DecodeResult.Value(Test1(Nil))
    codec.decode("f1=10") shouldBe DecodeResult.Value(Test1(List(10)))
    codec.decode("f1=10&f1=12") shouldBe DecodeResult.Value(Test1(List(10, 12)))
  }
}

case class CaseClassWithComplicatedName(complicatedName: Int)
