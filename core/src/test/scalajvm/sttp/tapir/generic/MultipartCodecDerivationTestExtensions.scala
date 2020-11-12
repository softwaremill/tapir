package sttp.tapir.generic

import java.io.File

import sttp.tapir.util.CompileUtil

trait MultipartCodecDerivationTestExtensions { self: MultipartCodecDerivationTest =>

  def createTempFile() = File.createTempFile("tapir", "test")

  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    val error = CompileUtil.interceptEval("""
                                            |import sttp.tapir._
                                            |trait NoCodecForThisTrait
                                            |case class Test5(f1: String, f2: NoCodecForThisTrait)
                                            |implicitly[Codec[String, Test5, CodecFormat.XWwwFormUrlencoded]]
                                            |""".stripMargin)

    error.message should include("Cannot find a codec between types: List[String] and NoCodecForThisTrait")
  }
}
