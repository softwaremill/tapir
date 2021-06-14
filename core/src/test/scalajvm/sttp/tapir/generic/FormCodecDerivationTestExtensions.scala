package sttp.tapir.generic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait FormCodecDerivationTestExtensions extends AnyFlatSpec with Matchers {
  it should "report a user-friendly error when a codec for a parameter cannot be found" in {
    assertTypeError("""
      |import sttp.tapir._
      |trait NoCodecForThisTrait
      |case class Test5(f1: String, f2: NoCodecForThisTrait)
      |implicitly[Codec[String, Test5, CodecFormat.XWwwFormUrlencoded]]
      |""".stripMargin)
  }
}
