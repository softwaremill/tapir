package sttp.tapir.serverless.aws.cdk.core

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.serverless.aws.cdk.core.Method._

class RequestTest extends AnyFunSuite with Matchers {

  test("encoding") {
    val e = endpoint.get.in("books?good")

    val x = Request.fromEndpoint(e).get // fixme
    val stringify = x.path.stringify
    assert("books%3Fgood" == stringify)
    assert(GET == x.method)
  }

  // fixme: use table tests
  test("basic test 1") {
    val e = endpoint.get.in("books")

    val x = Request.fromEndpoint(e).get
    assert("books" == x.path.stringify)
    assert(GET == x.method)
  }

  test("basic test 2") {
    val e = endpoint.get.in("books" / path[Int]("id"))

    val x = Request.fromEndpoint(e).get
    assert("books/{id}" == x.path.stringify)
    assert(GET == x.method)
  }

  test("basic test 3") { // fixme rename tests
    val e = endpoint.get.in("books" / path[Int] / path[Int])

    val x = Request.fromEndpoint(e).get
    assert("books/{param1}/{param2}" == x.path.stringify)
    assert(GET == x.method)
  }

  test("empty segment from tapir") { // fixme rename tests
    val e = endpoint.get.in("books" / "" / path[Int]("id"))
    assert("books/{id}" == Request.fromEndpoint(e).get.path.stringify)
  }

  test("empty segment") {
    assert("" == List("").toRequest(GET).path.stringify)
  }

  test("empty segment in the middle") {
    val maybeRequest = List("a", "", "b").toRequest(GET)
    assert("a/b" == maybeRequest.path.stringify)
  }

  test("no method") {
    val e = endpoint.in("books!")
    assert(Request.fromEndpoint(e).isEmpty)
  }

  test("unsupported method") {
    val e = endpoint.trace.in("var")
    assert(Request.fromEndpoint(e).isEmpty)
  }
}
