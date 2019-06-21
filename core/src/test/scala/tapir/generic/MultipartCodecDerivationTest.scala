package tapir.generic

import java.io.File

import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema._
import tapir.model.Part
import tapir.util.CompileUtil
import tapir.{Codec, DecodeResult, MediaType, RawPart}

class MultipartCodecDerivationTest extends FlatSpec with Matchers {
  it should "generate a codec for a one-arg case class" in {
    // given
    case class Test1(f1: Int)
    val codec = implicitly[Codec[Test1, MediaType.MultipartFormData, Seq[RawPart]]]

    // when
    toPartData(codec.encode(Test1(10))) shouldBe List(("f1", "10"))
    codec.decode(createStringParts(List(("f1", "10")))) shouldBe DecodeResult.Value(Test1(10))
  }

  it should "generate a codec for a two-arg case class" in {
    // given
    case class Test2(f1: String, f2: Int)
    val codec = implicitly[Codec[Test2, MediaType.MultipartFormData, Seq[RawPart]]]

    // when
    toPartData(codec.encode(Test2("v1", 10))) shouldBe List(("f1", "v1"), ("f2", "10"))
    toPartData(codec.encode(Test2("John Smith Ą", 10))) shouldBe List(("f1", "John Smith Ą"), ("f2", "10"))

    codec.decode(createStringParts(List(("f1", "v1"), ("f2", "10")))) shouldBe DecodeResult.Value(Test2("v1", 10))
    codec.decode(createStringParts(List(("f1", "v1")))) shouldBe DecodeResult.Missing
  }

  it should "generate a codec for a case class with optional parameters" in {
    // given
    case class Test4(f1: Option[String], f2: Int)
    val codec = implicitly[Codec[Test4, MediaType.MultipartFormData, Seq[RawPart]]]

    // when
    toPartData(codec.encode(Test4(Some("v1"), 10))) shouldBe List(("f1", "v1"), ("f2", "10"))
    toPartData(codec.encode(Test4(None, 10))) shouldBe List(("f2", "10"))

    codec.decode(createStringParts(List(("f1", "v1"), ("f2", "10")))) shouldBe DecodeResult.Value(Test4(Some("v1"), 10))
    codec.decode(createStringParts(List(("f2", "10")))) shouldBe DecodeResult.Value(Test4(None, 10))
  }

  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    val error = CompileUtil.interceptEval("""
                                            |import tapir._
                                            |trait NoCodecForThisTrait
                                            |case class Test5(f1: String, f2: NoCodecForThisTrait)
                                            |implicitly[Codec[Test5, MediaType.MultipartFormData, Seq[RawPart]]]
                                            |""".stripMargin)

    error.message should include("Cannot find a codec for type: NoCodecForThisTrait")
  }

  it should "use the right schema for a case class with part metadata" in {
    // given
    case class Test6(f1: String, f2: Int)
    val codec = implicitly[Codec[Test6, MediaType.MultipartFormData, Seq[RawPart]]]

    // when
    codec.meta.schema shouldBe SProduct(
      SObjectInfo("tapir.generic.MultipartCodecDerivationTest.<local MultipartCodecDerivationTest>.Test6"),
      List(("f1", SString), ("f2", SInteger)),
      List("f1", "f2")
    )
  }

  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test1(f1: Part[File], f2: Int)
    val codec = implicitly[Codec[Test1, MediaType.MultipartFormData, Seq[RawPart]]]

    // when
    codec.meta.schema shouldBe SProduct(
      SObjectInfo("tapir.generic.MultipartCodecDerivationTest.<local MultipartCodecDerivationTest>.Test1"),
      List(("f1", SBinary), ("f2", SInteger)),
      List("f1", "f2")
    )
  }

  it should "generate a codec for a one-arg case class using snake-case naming transformation" in {
    // given
    implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
    val codec = implicitly[Codec[CaseClassWithComplicatedName, MediaType.MultipartFormData, Seq[RawPart]]]

    // when
    toPartData(codec.encode(CaseClassWithComplicatedName(10))) shouldBe List(("complicated_name", "10"))
    codec.decode(createStringParts(List(("complicated_name", "10")))) shouldBe DecodeResult.Value(CaseClassWithComplicatedName(10))
  }

  it should "generate a codec for a one-arg case class with list" in {
    // given
    case class Test1(f1: List[Int])
    val codec = implicitly[Codec[Test1, MediaType.MultipartFormData, Seq[RawPart]]]

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
    val codec = implicitly[Codec[Test1, MediaType.MultipartFormData, Seq[RawPart]]]

    val instance = Test1(Part("?", Map("a1" -> "b1"), List(("X-Y", "a-b")), 10), "v2")
    val parts = List(
      Part("f1", Map("a1" -> "b1"), List(("X-Y", "a-b")), "10"),
      Part("f2", Map(), Nil, "v2")
    )

    // when
    codec.encode(instance) shouldBe parts
    codec.decode(parts) shouldBe DecodeResult.Value(instance.copy(f1 = instance.f1.copy(name = "f1")))
  }

  it should "generate a codec for a case class with file part" in {
    // given
    case class Test1(f1: File)
    val codec = implicitly[Codec[Test1, MediaType.MultipartFormData, Seq[RawPart]]]
    val f = File.createTempFile("tapir", "test")

    try {
      // when
      codec.encode(Test1(f)) shouldBe List(Part("f1", Map("filename" -> f.getName), Nil, f))
      codec.decode(List(Part("f1", Map("filename" -> f.getName), Nil, f))) shouldBe DecodeResult.Value(Test1(f))
    } finally {
      f.delete()
    }
  }

  it should "use the right schema for an optional file part with metadata 2" in {
    // given
    case class Test1(f1: Part[Option[File]], f2: Int)
    val codec = implicitly[Codec[Test1, MediaType.MultipartFormData, Seq[RawPart]]]
    val f = File.createTempFile("tapir", "test")

    // when
    try {
      // when
      codec.encode(Test1(Part("?", Map("a1" -> "b1"), Nil, Some(f)), 12)) shouldBe List(
        Part("f1", Map("a1" -> "b1"), Nil, f),
        Part("f2", "12")
      )
      codec.decode(List(Part("f1", Map("filename" -> f.getName), Nil, f), Part("f2", "12"))) shouldBe DecodeResult.Value(
        Test1(Part("f1", Map("filename" -> f.getName), Nil, Some(f)), 12)
      )
    } finally {
      f.delete()
    }

    codec.encode(Test1(Part("f1", None), 12)) shouldBe List(Part("f2", "12"))
    codec.decode(List(Part("f2", "12"))) shouldBe DecodeResult.Value(Test1(Part("f1", None), 12))
  }

  private def toPartData(parts: Seq[RawPart]): Seq[(String, Any)] = parts.map(p => (p.name, p.body))

  private def createStringParts(namesWithBodies: List[(String, String)]): List[RawPart] = namesWithBodies.map {
    case (name, body) =>
      Part(name, body)
  }
}
