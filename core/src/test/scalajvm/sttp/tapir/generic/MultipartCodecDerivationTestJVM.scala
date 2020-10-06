package sttp.tapir.generic

import java.io.File

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.{MediaType, Part}
import sttp.tapir.{DecodeResult, FieldName, MultipartCodec, Schema}
import sttp.tapir.SchemaType.{SObjectInfo, SProduct}
import sttp.tapir.util.CompileUtil

class MultipartCodecDerivationTestJVM extends AnyFlatSpec with Matchers {
  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    val error = CompileUtil.interceptEval("""
                                            |import sttp.tapir._
                                            |trait NoCodecForThisTrait
                                            |case class Test5(f1: String, f2: NoCodecForThisTrait)
                                            |implicitly[Codec[String, Test5, CodecFormat.XWwwFormUrlencoded]]
                                            |""".stripMargin)

    error.message should include("Cannot find a codec between types: List[String] and NoCodecForThisTrait")
  }

  it should "generate a codec for a case class with file part" in {
    // given
    case class Test1(f1: File)
    val codec = implicitly[MultipartCodec[Test1]].codec
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
    val codec = implicitly[MultipartCodec[Test1]].codec
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

  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test1(f1: Part[File], f2: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec

    // when
    codec.schema.map(_.schemaType) shouldBe Some(
      SProduct(
        SObjectInfo("sttp.tapir.generic.MultipartCodecDerivationTestJVM.<local MultipartCodecDerivationTestJVM>.Test1"),
        List((FieldName("f1"), implicitly[Schema[File]]), (FieldName("f2"), implicitly[Schema[Int]]))
      )
    )
  }
}
