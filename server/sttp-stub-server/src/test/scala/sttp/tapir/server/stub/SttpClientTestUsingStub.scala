package sttp.tapir.server.stub

import org.scalatest.{FunSuite, Matchers}
import sttp.client._
import sttp.client.monad.{IdMonad, MonadError}
import sttp.client.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir.Endpoint
import sttp.tapir.client.sttp._
import sttp.tapir.tests._

class SttpClientTestUsingStub extends FunSuite with Matchers {
  implicit val idMonad: MonadError[Identity] = IdMonad

  testClient(in_header_out_header_unit_extended, ((), "y"), Right(((), "x"))) { response =>
    response.header("B") shouldBe Some("2")
    response.header("Y") shouldBe Some("x")
  }

  testClient(in_3query_out_3header_mapped_to_tuple, ("a", "b", "c", "d"), Right(("e", "f", "g", "h"))) { response =>
    response.header("P1") shouldBe Some("e")
    response.header("P2") shouldBe Some("f")
    response.header("P3") shouldBe Some("h")
  }

  testClient(in_2query_out_2query_mapped_to_unit, "a", Right("b")) { response =>
    response.header("P1") shouldBe Some("DEFAULT_HEADER")
    response.header("P2") shouldBe Some("b")
  }

  def testClient[I, E, O](endpoint: Endpoint[I, E, O, Nothing], inputValue: I, outputValue: Either[E, O])(
      verifyResponse: Response[Either[E, O]] => Unit
  ): Unit = {
    test(s"calling $endpoint with $inputValue should result in $outputValue") {
      implicit val backend: SttpBackendStub[Identity, _] = outputValue match {
        case Left(e) =>
          SttpBackendStub(idMonad)
            .whenRequestMatches(endpoint)
            .thenError(e, StatusCode.BadRequest)
        case Right(o) =>
          SttpBackendStub(idMonad)
            .whenRequestMatches(endpoint)
            .thenSuccess(o)
      }
      val response: Identity[Response[Either[E, O]]] =
        backend.send(endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(inputValue))
      verifyResponse(response)
    }
  }
}
