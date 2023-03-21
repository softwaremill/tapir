package sttp.tapir.serverless.aws.cdk.internal

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.serverless.aws.cdk.internal.Segment._

class SegmentTest extends AnyFunSuite with Matchers with OptionValues {
  test("building fixed segment") {
    val url = Segment("hello")
    url.value shouldBe a[Fixed]
  }

  test("with bracket") {
    val url = Segment("hel{ooo}")
    url.value shouldBe a[Fixed]
  }

  test("empty string should return None") {
    Segment("") shouldBe None
  }

  test("fixed uppercase") {
    val url = Segment("Hello").value

    url.raw shouldBe "Hello"
    url.toString shouldBe "Hello"
  }

  test("parameter uppercase") {
    val url = Segment("{Hello}").value

    url.raw shouldBe "Hello"
    url.toString shouldBe "{Hello}"
  }
}
