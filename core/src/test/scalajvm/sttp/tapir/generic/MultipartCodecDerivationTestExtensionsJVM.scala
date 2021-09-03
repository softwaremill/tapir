package sttp.tapir.generic

import java.io.File

trait MultipartCodecDerivationTestExtensionsJVM { self: MultipartCodecDerivationTestJVM =>

  def createTempFile() = File.createTempFile("tapir", "test")

  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    assertTypeError("""
      |import sttp.tapir._
      |trait NoCodecForThisTrait
      |case class Test5(f1: String, f2: NoCodecForThisTrait)
      |implicitly[Codec[String, Test5, CodecFormat.XWwwFormUrlencoded]]
      |""".stripMargin)
  }
}
