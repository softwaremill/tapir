package sttp.tapir.generic

import java.io.File

import com.github.ghik.silencer.silent
import org.scalatest.{FlatSpec, Matchers}
import sttp.model.{Header, MediaType, Part}
import sttp.tapir.SchemaType._
import sttp.tapir.util.CompileUtil
import sttp.tapir.{DecodeResult, MultipartCodec, RawPart, Schema, Validator}

@silent("discarded")
@silent("never used")
class MultipartCodecDerivationTest extends FlatSpec with Matchers {
  it should "generate a codec for a one-arg case class" in {
    // given
    case class Test1(f1: Int)
    val codec = implicitly[MultipartCodec[Test1]]._2

    // when
    toPartData(codec.encode(Test1(10))) shouldBe List(("f1", "10"))
    codec.decode(createStringParts(List(("f1", "10")))) shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a two-arg case class" in {
    // given
    case class Test2(f1: String, f2: Int)
    val codec = implicitly[MultipartCodec[Test2]]._2

    // when
    toPartData(codec.encode(Test2("v1", 10))) shouldBe List(("f1", "v1"), ("f2", "10"))
    toPartData(codec.encode(Test2("John Smith Ą", 10))) shouldBe List(("f1", "John Smith Ą"), ("f2", "10"))

    codec.decode(createStringParts(List(("f1", "v1"), ("f2", "10")))) shouldBe DecodeResult.Value(Test2("v1", 10))
    codec.decode(createStringParts(List(("f1", "v1")))) shouldBe DecodeResult.Missing
  }

  it should "generate a codec for a case class with optional parameters" in {
    // given
    case class Test4(f1: Option[String], f2: Int)
    val codec = implicitly[MultipartCodec[Test4]]._2

    // when
    toPartData(codec.encode(Test4(Some("v1"), 10))) shouldBe List(("f1", "v1"), ("f2", "10"))
    toPartData(codec.encode(Test4(None, 10))) shouldBe List(("f2", "10"))

    codec.decode(createStringParts(List(("f1", "v1"), ("f2", "10")))) shouldBe DecodeResult.Value(Test4(Some("v1"), 10))
    codec.decode(createStringParts(List(("f2", "10")))) shouldBe DecodeResult.Value(Test4(None, 10))
  }

  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    val error = CompileUtil.interceptEval("""
                                            |import sttp.tapir._
                                            |trait NoCodecForThisTrait
                                            |case class Test5(f1: String, f2: NoCodecForThisTrait)
                                            |implicitly[MultipartCodec[Test5]]
                                            |""".stripMargin)

    error.message should include("Cannot find a codec between a List[T] for some basic type T and: NoCodecForThisTrait")
  }

  it should "use the right schema for a case class with part metadata" in {
    // given
    case class Test6(f1: String, f2: Int)
    val codec = implicitly[MultipartCodec[Test6]]._2

    // when
    codec.schema.map(_.schemaType) shouldBe Some(
      SProduct(
        SObjectInfo("sttp.tapir.generic.MultipartCodecDerivationTest.<local MultipartCodecDerivationTest>.Test6"),
        List(("f1", implicitly[Schema[String]]), ("f2", implicitly[Schema[Int]]))
      )
    )
  }

  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test1(f1: Part[File], f2: Int)
    val codec = implicitly[MultipartCodec[Test1]]._2

    // when
    codec.schema.map(_.schemaType) shouldBe Some(
      SProduct(
        SObjectInfo("sttp.tapir.generic.MultipartCodecDerivationTest.<local MultipartCodecDerivationTest>.Test1"),
        List(("f1", implicitly[Schema[File]]), ("f2", implicitly[Schema[Int]]))
      )
    )
  }

  it should "generate a codec for a one-arg case class using snake-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
    val codec = implicitly[MultipartCodec[CaseClassWithComplicatedName]]._2

    // when
    toPartData(codec.encode(CaseClassWithComplicatedName(10))) shouldBe List(("complicated_name", "10"))
    codec.decode(createStringParts(List(("complicated_name", "10")))) shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class with list" in {
    // given
    case class Test1(f1: List[Int])
    val codec = implicitly[MultipartCodec[Test1]]._2

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
    val codec = implicitly[MultipartCodec[Test1]]._2

    val instance = Test1(Part("?", 10, otherDispositionParams = Map("a1" -> "b1"), headers = List(Header.unsafeApply("X-Y", "a-b"))), "v2")
    val parts = List(
      Part(
        "f1",
        "10",
        otherDispositionParams = Map("a1" -> "b1"),
        headers = List(Header.unsafeApply("X-Y", "a-b"), Header.contentType(MediaType.TextPlain))
      ),
      Part("f2", "v2", contentType = Some(MediaType.TextPlain))
    )

    // when
    codec.encode(instance) shouldBe parts
    codec.decode(parts) shouldBe DecodeResult.Value(
      instance.copy(f1 =
        Part(
          "f1",
          10,
          otherDispositionParams = Map("a1" -> "b1"),
          headers = List(Header.unsafeApply("X-Y", "a-b"), Header.contentType(MediaType.TextPlain))
        )
      )
    )
  }

  it should "generate a codec for a case class with file part" in {
    // given
    case class Test1(f1: File)
    val codec = implicitly[MultipartCodec[Test1]]._2
    val f = File.createTempFile("tapir", "test")

    try {
      // when
      codec.encode(Test1(f)) shouldBe List(Part("f1", f, fileName = Some(f.getName), contentType = Some(MediaType.ApplicationOctetStream)))
      codec.decode(List(Part("f1", f, fileName = Some(f.getName)))) shouldBe DecodeResult.Value(Test1(f))
    } finally {
      f.delete()
    }
  }

  it should "use the right schema for an optional file part with metadata 2" in {
    // given
    case class Test1(f1: Part[Option[File]], f2: Int)
    val codec = implicitly[MultipartCodec[Test1]]._2
    val f = File.createTempFile("tapir", "test")

    // when
    try {
      // when
      codec.encode(Test1(Part("?", Some(f), otherDispositionParams = Map("a1" -> "b1")), 12)) shouldBe List(
        Part("f1", f, otherDispositionParams = Map("a1" -> "b1"), contentType = Some(MediaType.ApplicationOctetStream)),
        Part("f2", "12", contentType = Some(MediaType.TextPlain))
      )
      codec.decode(List(Part("f1", f, fileName = Some(f.getName)), Part("f2", "12"))) shouldBe DecodeResult.Value(
        Test1(Part("f1", Some(f), fileName = Some(f.getName)), 12)
      )
    } finally {
      f.delete()
    }

    codec.encode(Test1(Part("f1", None), 12)) shouldBe List(Part("f2", "12", contentType = Some(MediaType.TextPlain)))
    codec.decode(List(Part("f2", "12"))) shouldBe DecodeResult.Value(Test1(Part("f1", None), 12))
  }

  it should "generate a codec for a one-arg case class with implicit validator" in {
    // given
    implicit val v: Validator[Int] = Validator.min(5)
    case class Test1(f1: Int)
    val codec = implicitly[MultipartCodec[Test1]]._2

    // when
    toPartData(codec.encode(Test1(10))) shouldBe List(("f1", "10"))

    codec.decode(createStringParts(List(("f1", "0")))) shouldBe an[DecodeResult.InvalidValue]
    codec.decode(createStringParts(List(("f1", "10")))) shouldBe DecodeResult.Value(Test1(10))
  }

  private def toPartData(parts: Seq[RawPart]): Seq[(String, Any)] = parts.map(p => (p.name, p.body))

  private def createStringParts(namesWithBodies: List[(String, String)]): List[RawPart] = namesWithBodies.map {
    case (name, body) =>
      Part(name, body)
  }
}
