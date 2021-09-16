package sttp.tapir.client.http4s

import cats.effect._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

class Http4sClientRequestTests extends AnyFunSuite with Matchers {
  test("should exclude optional query parameter when its value is None") {
    // given
    val testEndpoint = endpoint.get.in(query[Option[String]]("param"))

    // when
    val (http4sRequest, _) = Http4sClientInterpreter[IO]()
      .toRequest(testEndpoint, baseUri = None)
      .apply(None)

    // then
    http4sRequest.queryString shouldBe empty
  }
}
