package sttp.tapir.json.jsoniter.bundle

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult
import sttp.tapir.generic.auto.*

class ConfiguredJsonValueCodecTest extends AnyFlatSpecLike with Matchers:
  case class Test(v1: String, v2: Int) derives ConfiguredJsonValueCodec

  it should "encode and decode using tapir's codec" in {
    val tapirCodec = jsonBody[Test].codec

    val actual = tapirCodec.decode("""{"v1":"test","v2":42}""")
    actual shouldBe DecodeResult.Value(Test("test", 42))

    tapirCodec.encode(Test("test", 42)) shouldBe """{"v1":"test","v2":42}"""
  }
