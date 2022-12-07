package sttp.tapir.serverless.aws.cdk.internal

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import sttp.tapir._
import sttp.tapir.serverless.aws.cdk.internal.Method._

class RequestTest extends AnyFunSuite with OptionValues {
  test("url encoding") {
    val e = endpoint.get.in("books?good")

    val request = Request.fromEndpoint(e).value

    assert("books%3Fgood" == request.path.stringify)
    assert(GET == request.method)
  }

  Seq(
    (endpoint.get.in("books"), "books", GET),
    (endpoint.post.in("books" / path[Int]("id")), "books/{id}", POST),
    (endpoint.put.in("books" / path[Int] / path[Int]), "books/{param1}/{param2}", PUT)
  ).foreach { case (endpoint, expectedPath, method) =>
    test(s"endpoint '${endpoint.show}' should be parsed to $expectedPath") {
      val request = Request.fromEndpoint(endpoint).value
      assert(expectedPath == request.path.stringify)
      assert(method == request.method)
    }
  }

  test("empty segment from tapir") {
    val e = endpoint.get.in("books" / "" / path[Int]("id"))
    assert("books/{id}" == Request.fromEndpoint(e).value.path.stringify)
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
}
