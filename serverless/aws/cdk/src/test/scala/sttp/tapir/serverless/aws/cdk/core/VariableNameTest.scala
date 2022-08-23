package sttp.tapir.serverless.aws.cdk.core

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class VariableNameTest extends AnyFunSuite with Matchers {

  val testData = List(
    ("hello!", "hello"),
    ("hello%3F", "hello"),
    ("", "v"), // empty
    ("123", "p123"), // numeric
    ("abc-12", "abc-12"),
    ("abc_12", "abc_12"),
    ("abc-@12", "abc-12"),
    (
      "veryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongName",
      "veryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLong"
    )
  )

  test("name") {
    for ((input, expected) <- testData) {
      val name = VariableName(input).toString
      assert(expected == name)
    }
  }
}
