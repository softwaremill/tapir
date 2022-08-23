package sttp.tapir.serverless.aws.cdk.core

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SegmentTest extends AnyFunSuite with Matchers {

  test("building fixed segment") {
    val url = Segment("hello")
    assert(url.get.isInstanceOf[Fixed])
  }

  test("with bracket") {
    val url = Segment("hel{ooo}")
    assert(url.get.isInstanceOf[Fixed]) // this is ok (todo: explain why)
  }

  test("empty") {
    assert(Segment("").isEmpty)
  }
}
