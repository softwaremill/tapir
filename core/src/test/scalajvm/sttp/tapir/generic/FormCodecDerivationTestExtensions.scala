package sttp.tapir.generic

import com.github.ghik.silencer.silent
import sttp.tapir.util.CompileUtil
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

@silent("never used")
trait FormCodecDerivationTestExtensions extends AnyFlatSpec with Matchers {
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
