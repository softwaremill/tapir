package sttp.tapir.generic

import sttp.model.{Header, MediaType, Part}
import sttp.tapir.generic.auto._
import sttp.tapir.SchemaType._
import sttp.tapir.{DecodeResult, FieldName, TapirFile, FileRange, MultipartCodec, RawPart, Schema, Validator}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.encodedName
import sttp.tapir.TestUtil.field

import java.nio.charset.StandardCharsets

class MultipartCodecDerivationTest extends AnyFlatSpec with MultipartCodecDerivationTestExtensions with Matchers {
  it should "generate a codec for a one-arg case class" in {
    // given
    case class Test1(f1: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec

    // when
    toPartData(codec.encode(Test1(10))) shouldBe List(("f1", "10"))
    codec.decode(createStringParts(List(("f1", "10")))) shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a two-arg case class" in {
    // given
    case class Test2(f1: String, f2: Int)
    val codec = implicitly[MultipartCodec[Test2]].codec

    // when
    toPartData(codec.encode(Test2("v1", 10))) shouldBe List(("f1", "v1"), ("f2", "10"))
    toPartData(codec.encode(Test2("John Smith Ą", 10))) shouldBe List(("f1", "John Smith Ą"), ("f2", "10"))

    codec.decode(createStringParts(List(("f1", "v1"), ("f2", "10")))) shouldBe DecodeResult.Value(Test2("v1", 10))
    codec.decode(createStringParts(List(("f1", "v1")))) should matchPattern {
      case DecodeResult.Error("", DecodeResult.Error.MultipartDecodeException(List(("f2", DecodeResult.Missing)))) =>
    }
  }

  it should "generate a codec for a case class with optional parameters" in {
    // given
    case class Test4(f1: Option[String], f2: Int)
    val codec = implicitly[MultipartCodec[Test4]].codec

    // when
    toPartData(codec.encode(Test4(Some("v1"), 10))) shouldBe List(("f1", "v1"), ("f2", "10"))
    toPartData(codec.encode(Test4(None, 10))) shouldBe List(("f2", "10"))

    codec.decode(createStringParts(List(("f1", "v1"), ("f2", "10")))) shouldBe DecodeResult.Value(Test4(Some("v1"), 10))
    codec.decode(createStringParts(List(("f2", "10")))) shouldBe DecodeResult.Value(Test4(None, 10))
  }

  it should "use the right schema for a case class with part metadata" in {
    // given
    case class Test6(f1: String, f2: Int)
    val codec = implicitly[MultipartCodec[Test6]].codec

    // when
    codec.schema.name shouldBe Some(SName("sttp.tapir.generic.MultipartCodecDerivationTest.<local MultipartCodecDerivationTest>.Test6"))
    codec.schema.schemaType shouldBe
      SProduct[Test6](
        List(field(FieldName("f1"), implicitly[Schema[String]]), field(FieldName("f2"), implicitly[Schema[Int]]))
      )
  }

  it should "generate a codec for a one-arg case class using snake-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
    val codec = implicitly[MultipartCodec[CaseClassWithComplicatedName]].codec

    // when
    toPartData(codec.encode(CaseClassWithComplicatedName(10))) shouldBe List(("complicated_name", "10"))
    codec.decode(createStringParts(List(("complicated_name", "10")))) shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class using screaming-snake-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withScreamingSnakeCaseMemberNames
    val codec = implicitly[MultipartCodec[CaseClassWithComplicatedName]].codec

    // when
    toPartData(codec.encode(CaseClassWithComplicatedName(10))) shouldBe List(("COMPLICATED_NAME", "10"))
    codec.decode(createStringParts(List(("COMPLICATED_NAME", "10")))) shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class with list" in {
    // given
    case class Test1(f1: List[Int])
    val codec = implicitly[MultipartCodec[Test1]].codec

    // when
    toPartData(codec.encode(Test1(Nil))) shouldBe Nil
    toPartData(codec.encode(Test1(List(10)))) shouldBe List(("f1", "10"))
    toPartData(codec.encode(Test1(List(10, 12)))) shouldBe List(("f1", "10"), ("f1", "12"))

    codec.decode(createStringParts(Nil)) shouldBe DecodeResult.Value(Test1(Nil))
    codec.decode(createStringParts(List(("f1", "10")))) shouldBe DecodeResult.Value(Test1(List(10)))
    codec.decode(createStringParts(List(("f1", "10"), ("f1", "12")))) shouldBe DecodeResult.Value(Test1(List(10, 12)))
  }

  it should "generate a codec for a case class with part metadata" in {
    // given
    case class Test1(f1: Part[Int], f2: String)
    val codec = implicitly[MultipartCodec[Test1]].codec

    val instance = Test1(Part("?", 10, otherDispositionParams = Map("a1" -> "b1"), headers = List(Header.unsafeApply("X-Y", "a-b"))), "v2")
    val parts = List(
      Part(
        "f1",
        "10",
        otherDispositionParams = Map("a1" -> "b1"),
        headers = List(Header.unsafeApply("X-Y", "a-b"), Header.contentType(MediaType.TextPlain.charset(StandardCharsets.UTF_8)))
      ),
      Part("f2", "v2", contentType = Some(MediaType.TextPlain.charset(StandardCharsets.UTF_8)))
    )

    // when
    codec.encode(instance) shouldBe parts
    codec.decode(parts) shouldBe DecodeResult.Value(
      instance.copy(f1 =
        Part(
          "f1",
          10,
          otherDispositionParams = Map("a1" -> "b1"),
          headers = List(Header.unsafeApply("X-Y", "a-b"), Header.contentType(MediaType.TextPlain.charset(StandardCharsets.UTF_8)))
        )
      )
    )
  }

  it should "generate a codec for a one-arg case class with implicit validator" in {
    // given
    implicit val s: Schema[Int] = Schema.schemaForInt.validate(Validator.min(5))
    case class Test1(f1: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec

    // when
    toPartData(codec.encode(Test1(10))) shouldBe List(("f1", "10"))

    codec.decode(createStringParts(List(("f1", "0")))) shouldBe an[DecodeResult.InvalidValue]
    codec.decode(createStringParts(List(("f1", "10")))) shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec with a custom field name" in {
    // given
    case class Test1(@encodedName("g1") f1: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec

    // when
    toPartData(codec.encode(Test1(10))) shouldBe List(("g1", "10"))
    codec.decode(createStringParts(List(("g1", "10")))) shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a case class with file part" in {
    // given
    case class Test1(f1: TapirFile)
    val codec = implicitly[MultipartCodec[Test1]].codec
    val f = createTempFile()

    val x = codec.encode(Test1(f))

    try {
      // when
      codec.encode(Test1(f)) shouldBe List(
        Part("f1", FileRange(f), fileName = Some(f.getName), contentType = Some(MediaType.ApplicationOctetStream))
      )
      codec.decode(List(Part("f1", FileRange(f), fileName = Some(f.getName)))) shouldBe DecodeResult.Value(Test1(f))
    } finally {
      f.delete()
    }
  }

  it should "use the right schema for an optional file part with metadata 2" in {
    // given
    case class Test1(f1: Option[Part[TapirFile]], f2: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec
    val f = createTempFile()

    // when
    try {
      // when
      codec.encode(Test1(Some(Part("?", f, otherDispositionParams = Map("a1" -> "b1"))), 12)) shouldBe List(
        Part(
          "f1",
          FileRange(f),
          otherDispositionParams = Map("a1" -> "b1", "filename" -> f.getName),
          contentType = Some(MediaType.ApplicationOctetStream)
        ),
        Part("f2", "12", contentType = Some(MediaType.TextPlain.charset(StandardCharsets.UTF_8)))
      )
      codec.decode(List(Part("f1", FileRange(f), fileName = Some(f.getName)), Part("f2", "12"))) shouldBe DecodeResult.Value(
        Test1(Some(Part("f1", f, fileName = Some(f.getName))), 12)
      )
    } finally {
      f.delete()
    }

    codec.encode(Test1(None, 12)) shouldBe List(
      Part("f2", "12", contentType = Some(MediaType.TextPlain.charset(StandardCharsets.UTF_8)))
    )
    codec.decode(List(Part("f2", "12"))) shouldBe DecodeResult.Value(Test1(None, 12))
  }

  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test1(f1: Part[TapirFile], f2: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec

    // when
    codec.schema.name shouldBe Some(SName("sttp.tapir.generic.MultipartCodecDerivationTest.<local MultipartCodecDerivationTest>.Test1"))
    codec.schema.schemaType shouldBe
      SProduct[Test1](
        List(field(FieldName("f1"), implicitly[Schema[TapirFile]]), field(FieldName("f2"), implicitly[Schema[Int]]))
      )
  }

  private def toPartData(parts: Seq[RawPart]): Seq[(String, Any)] = parts.map(p => (p.name, p.body))

  private def createStringParts(namesWithBodies: List[(String, String)]): List[RawPart] =
    namesWithBodies.map { case (name, body) =>
      Part(name, body)
    }
}
