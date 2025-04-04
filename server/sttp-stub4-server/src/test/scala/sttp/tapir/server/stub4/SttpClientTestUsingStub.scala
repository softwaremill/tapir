package sttp.tapir.server.stub4

import sttp.model.StatusCode
import sttp.tapir.PublicEndpoint
import sttp.tapir.client.sttp4._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.monad.MonadError
import sttp.tapir.tests.Mapping.{
  in_2query_out_2query_mapped_to_unit,
  in_3query_out_3header_mapped_to_tuple,
  in_header_out_header_unit_extended
}
import sttp.shared.Identity
import sttp.monad.IdentityMonad
import sttp.client4._
import sttp.client4.testing.BackendStub
import sttp.client4.testing.SyncBackendStub

class SttpClientTestUsingStub extends AnyFunSuite with Matchers {
  implicit val idMonad: MonadError[Identity] = IdentityMonad

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

  def testClient[I, E, O](endpoint: PublicEndpoint[I, E, O, Any], inputValue: I, outputValue: Either[E, O])(
      verifyResponse: Response[Either[E, O]] => Unit
  ): Unit = {
    test(s"calling $endpoint with $inputValue should result in $outputValue") {
      val backend: SyncBackendStub = outputValue match {
        case Left(e) =>
          BackendStub.synchronous
            .whenRequestMatchesEndpoint(endpoint)
            .thenError(e, StatusCode.BadRequest)
        case Right(o) =>
          BackendStub.synchronous
            .whenRequestMatchesEndpoint(endpoint)
            .thenSuccess(o)
      }
      val response: Identity[Response[Either[E, O]]] =
        SttpClientInterpreter().toRequestThrowDecodeFailures(endpoint, Some(uri"http://test.com")).apply(inputValue).send(backend)
      verifyResponse(response)
    }
  }
}
