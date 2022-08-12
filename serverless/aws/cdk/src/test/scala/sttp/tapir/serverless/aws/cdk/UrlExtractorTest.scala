package sttp.tapir.serverless.aws.cdk

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

class UrlExtractorTest extends AnyFunSuite with Matchers {

  //fixme: use table tests
  test("basic test 1") {
    val e = endpoint.get.in("books")
    val result = UrlExtractor.process(e)

    assert("books" == result.getPath)
    assert("GET" == result.method)
  }

  test("basic test 2") {
    val e = endpoint.get.in("books" / path[Int]("id"))
    val result = UrlExtractor.process(e)

    assert("books/{id}" == result.getPath)
    assert("GET" == result.method)
  }

  test("basic test 3") {
    val e = endpoint.get.in("books" / path[Int] / path[Int])
    val result = UrlExtractor.process(e)

    assert("books/{param0}/{param1}" == result.getPath)
    assert("GET" == result.method)
  }
}
