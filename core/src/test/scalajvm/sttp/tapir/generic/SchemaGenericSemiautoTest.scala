package sttp.tapir.generic

import com.github.ghik.silencer.silent
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AsyncFlatSpec
import sttp.tapir.util.CompileUtil

@silent("never used")
class SchemaGenericSemiautoTest extends AsyncFlatSpec with Matchers {

    "Schema semiauto derivation" should "fail and hint if an implicit instance is missing" in {
        val error = CompileUtil.interceptEval("""
        |import sttp.tapir._
        |case class Unknown(str: String)
        |case class Example(str: String, unknown: Unknown)
        |Schema.derive[Example]
        |""".stripMargin)

        error.message should include ("magnolia: could not find Schema.Typeclass for type Unknown")
        error.message should include ("in parameter 'unknown' of product type Example")
    }

}
