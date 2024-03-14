package sttp.tapir.generic

import java.math.{BigDecimal => JBigDecimal}
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.generic.auto._
import sttp.tapir.{Codec, CodecFormat, DecodeResult, FieldName, Schema, Validator}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.encodedName
import sttp.tapir.TestUtil.field

class FormCodecDerivationTest extends AnyFlatSpec with FormCodecDerivationTestExtensions with Matchers {
  it should "generate a codec for a one-arg case class" in {
    // given
    case class Test1(f1: Int)
    val codec = implicitly[Codec[String, Test1, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test1(10)) shouldBe "f1=10"
    codec.decode("f1=10") shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a two-arg case class" in {
    // given
    case class Test2(f1: String, f2: Int)
    val codec = implicitly[Codec[String, Test2, CodecFormat.XWwwFormUrlencoded]]

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
    val codec = implicitly[Codec[String, Test3, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test3("v1", 10, f3 = true)) shouldBe "f1=v1&f2=10&f3=true"
    codec.decode("f1=v1&f2=10&f3=true") shouldBe DecodeResult.Value(Test3("v1", 10, f3 = true))
  }

  it should "generate a codec for a case class with optional parameters" in {
    // given
    case class Test4(f1: Option[String], f2: Int)
    val codec = implicitly[Codec[String, Test4, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test4(Some("v1"), 10)) shouldBe "f1=v1&f2=10"
    codec.encode(Test4(None, 10)) shouldBe "f2=10"

    codec.decode("f1=v1&f2=10") shouldBe DecodeResult.Value(Test4(Some("v1"), 10))
    codec.decode("f2=10") shouldBe DecodeResult.Value(Test4(None, 10))
  }

  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test6(f1: String, f2: Int)
    val codec = implicitly[Codec[String, Test6, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.schema.name shouldBe Some(SName("sttp.tapir.generic.FormCodecDerivationTest.<local FormCodecDerivationTest>.Test6"))
    codec.schema.schemaType shouldBe
      SProduct[Test6](
        List(field(FieldName("f1"), implicitly[Schema[String]]), field(FieldName("f2"), implicitly[Schema[Int]]))
      )
  }

  it should "generate a codec for a one-arg case class using snake-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
    val codec = implicitly[Codec[String, CaseClassWithComplicatedName, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(CaseClassWithComplicatedName(10)) shouldBe "complicated_name=10"
    codec.decode("complicated_name=10") shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class using screaming-snake-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withScreamingSnakeCaseMemberNames
    val codec = implicitly[Codec[String, CaseClassWithComplicatedName, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(CaseClassWithComplicatedName(10)) shouldBe "COMPLICATED_NAME=10"
    codec.decode("COMPLICATED_NAME=10") shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class using kebab-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withKebabCaseMemberNames
    val codec = implicitly[Codec[String, CaseClassWithComplicatedName, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(CaseClassWithComplicatedName(10)) shouldBe "complicated-name=10"
    codec.decode("complicated-name=10") shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class with list" in {
    // given
    case class Test1(f1: List[Int])
    val codec = implicitly[Codec[String, Test1, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test1(Nil)) shouldBe ""
    codec.encode(Test1(List(10))) shouldBe "f1=10"
    codec.encode(Test1(List(10, 12))) shouldBe "f1=10&f1=12"

    codec.decode("") shouldBe DecodeResult.Value(Test1(Nil))
    codec.decode("f1=10") shouldBe DecodeResult.Value(Test1(List(10)))
    codec.decode("f1=10&f1=12") shouldBe DecodeResult.Value(Test1(List(10, 12)))
  }

  it should "generate a codec for a one-arg case class with set" in {
    // given
    case class Test1(f1: Set[Int])
    val codec = implicitly[Codec[String, Test1, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test1(Set.empty)) shouldBe ""
    codec.encode(Test1(Set(10))) shouldBe "f1=10"
    codec.encode(Test1(Set(10, 12))) shouldBe "f1=10&f1=12"

    codec.decode("") shouldBe DecodeResult.Value(Test1(Set.empty))
    codec.decode("f1=10") shouldBe DecodeResult.Value(Test1(Set(10)))
    codec.decode("f1=10&f1=12") shouldBe DecodeResult.Value(Test1(Set(10, 12)))
    codec.decode("f1=10&f1=12&f1=12") shouldBe DecodeResult.Value(Test1(Set(10, 12)))
  }

  it should "generate a codec for a one-arg case class with vector" in {
    // given
    case class Test1(f1: Vector[Int])
    val codec = implicitly[Codec[String, Test1, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test1(Vector.empty)) shouldBe ""
    codec.encode(Test1(Vector(10))) shouldBe "f1=10"
    codec.encode(Test1(Vector(10, 12))) shouldBe "f1=10&f1=12"

    codec.decode("") shouldBe DecodeResult.Value(Test1(Vector.empty))
    codec.decode("f1=10") shouldBe DecodeResult.Value(Test1(Vector(10)))
    codec.decode("f1=10&f1=12") shouldBe DecodeResult.Value(Test1(Vector(10, 12)))
  }

  it should "generate a codec for a one-arg case class using implicit schema" in {
    // given
    case class Test1(f1: Int)
    implicit val s: Schema[Int] = Schema.schemaForInt.validate(Validator.min(5))
    val codec = implicitly[Codec[String, Test1, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test1(10)) shouldBe "f1=10"
    codec.decode("f1=0") shouldBe an[DecodeResult.InvalidValue]
    codec.decode("f1=10") shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a case class with simple types" in {
    // given
    case class Test1(
        f1: String,
        f2: Byte,
        f3: Short,
        f4: Int,
        f5: Long,
        f6: Float,
        f7: Double,
        f8: Boolean,
        f9: BigDecimal,
        f10: JBigDecimal
    )
    val test1 = Test1(
      f1 = "str",
      f2 = 42,
      f3 = 228,
      f4 = 322,
      f5 = 69,
      f6 = 27,
      f7 = 33,
      f8 = true,
      f9 = BigDecimal("1337.7331"),
      f10 = new JBigDecimal("31337.73313")
    )
    val codec = implicitly[Codec[String, Test1, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec
      .encode(test1)
      .shouldBe(
        // In Scala.js in general, a trailing .0 is omitted i.e. 1.0.toString == "1", instead of "1.0"
        // See https://www.scala-js.org/doc/semantics.html
        if (System.getProperty("java.vm.name") == "Scala.js")
          "f1=str&f2=42&f3=228&f4=322&f5=69&f6=27&f7=33&f8=true&f9=1337.7331&f10=31337.73313"
        else
          "f1=str&f2=42&f3=228&f4=322&f5=69&f6=27.0&f7=33.0&f8=true&f9=1337.7331&f10=31337.73313"
      )

    codec.decode("f1=str&f2=42&f3=228&f4=322&f5=69&f6=27.0&f7=33.0&f8=true&f9=1337.7331&f10=31337.73313") shouldBe DecodeResult.Value(test1)
  }

  it should "generate a codec with custom field name" in {
    // given
    case class Test1(@encodedName("g1") f1: Int)
    val codec = implicitly[Codec[String, Test1, CodecFormat.XWwwFormUrlencoded]]

    // when
    codec.encode(Test1(10)) shouldBe "g1=10"
    codec.decode("g1=10") shouldBe DecodeResult.Value(Test1(10))
  }
}

case class CaseClassWithComplicatedName(complicatedName: Int)
