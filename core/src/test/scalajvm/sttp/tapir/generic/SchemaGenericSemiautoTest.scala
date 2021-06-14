package sttp.tapir.generic

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AsyncFlatSpec

class SchemaGenericSemiautoTest extends AsyncFlatSpec with Matchers {
  "Schema semiauto derivation" should "fail and hint if an implicit instance is missing" in {
    assertTypeError("""
      |import sttp.tapir._
      |case class Unknown(str: String)
      |case class Example(str: String, unknown: Unknown)
      |Schema.derived[Example]
      |""".stripMargin)
  }
}
