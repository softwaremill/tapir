package sttp.tapir.generic

import org.scalatest.flatspec.AnyFlatSpec
import sttp.tapir.generic.auto._
import org.scalatest.matchers.should.Matchers
import sttp.model.{MediaType, Part}
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.TestUtil.field
import sttp.tapir.internal.TapirFile
import sttp.tapir.{DecodeResult, FieldName, MultipartCodec, Schema}

import java.io.File
import java.nio.charset.StandardCharsets

class MultipartCodecDerivationTestJVM extends AnyFlatSpec with MultipartCodecDerivationTestExtensionsJVM with Matchers {
  it should "generate a codec for a case class with file part" in {
    // given
    case class Test1(f1: File)
    val codec = implicitly[MultipartCodec[Test1]].codec
    val f = createTempFile()

    val x = codec.encode(Test1(f))

    try {
      // when
      codec.encode(Test1(f)) shouldBe List(
        Part("f1", TapirFile.fromFile(f), fileName = Some(f.getName), contentType = Some(MediaType.ApplicationOctetStream))
      )
      codec.decode(List(Part("f1", TapirFile.fromFile(f), fileName = Some(f.getName)))) shouldBe DecodeResult.Value(Test1(f))
    } finally {
      f.delete()
    }
  }

  it should "use the right schema for an optional file part with metadata 2" in {
    // given
    case class Test1(f1: Part[Option[File]], f2: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec
    val f = createTempFile()

    // when
    try {
      // when
      codec.encode(Test1(Part("?", Some(f), otherDispositionParams = Map("a1" -> "b1")), 12)) shouldBe List(
        Part("f1", TapirFile.fromFile(f), otherDispositionParams = Map("a1" -> "b1"), contentType = Some(MediaType.ApplicationOctetStream)),
        Part("f2", "12", contentType = Some(MediaType.TextPlain.charset(StandardCharsets.UTF_8)))
      )
      codec.decode(List(Part("f1", TapirFile.fromFile(f), fileName = Some(f.getName)), Part("f2", "12"))) shouldBe DecodeResult.Value(
        Test1(Part("f1", Some(f), fileName = Some(f.getName)), 12)
      )
    } finally {
      f.delete()
    }

    codec.encode(Test1(Part("f1", None), 12)) shouldBe List(
      Part("f2", "12", contentType = Some(MediaType.TextPlain.charset(StandardCharsets.UTF_8)))
    )
    codec.decode(List(Part("f2", "12"))) shouldBe DecodeResult.Value(Test1(Part("f1", None), 12))
  }
  it should "use the right schema for a two-arg case class" in {
    // given
    case class Test1(f1: Part[File], f2: Int)
    val codec = implicitly[MultipartCodec[Test1]].codec

    // when
    codec.schema.name shouldBe Some(SName("sttp.tapir.generic.MultipartCodecDerivationTestJVM.<local MultipartCodecDerivationTestJVM>.Test1"))
    codec.schema.schemaType shouldBe
      SProduct[Test1](
        List(field(FieldName("f1"), implicitly[Schema[File]]), field(FieldName("f2"), implicitly[Schema[Int]]))
      )
  }
}
