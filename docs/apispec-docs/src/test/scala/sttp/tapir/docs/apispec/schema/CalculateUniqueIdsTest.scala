package sttp.tapir.docs.apispec.schema

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CalculateUniqueIdsTest extends AnyFunSuite with Matchers {

  test("should suffix colliding names when not failing") {
    val ids = calculateUniqueIds[String](List("a", "b", "c"), s => if (s == "c") "a" else s, failOnDuplicateName = false)
    ids("a") shouldBe "a"
    ids("b") shouldBe "b"
    ids("c") shouldBe "a1"
  }

  test("should use the default schema-name message when failing and no message is given") {
    val thrown = intercept[IllegalStateException] {
      calculateUniqueIds[String](List("a", "c"), s => if (s == "c") "a" else s, failOnDuplicateName = true)
    }
    thrown.getMessage should include("Duplicate schema names found: a")
    thrown.getMessage should include("customize the schemaName function")
  }

  test("should use a caller-supplied message when failing") {
    val thrown = intercept[IllegalStateException] {
      calculateUniqueIds[String](
        List("a", "c"),
        s => if (s == "c") "a" else s,
        failOnDuplicateName = true,
        baseNames => s"boom: ${baseNames.mkString(",")}"
      )
    }
    thrown.getMessage shouldBe "boom: a"
  }
}
