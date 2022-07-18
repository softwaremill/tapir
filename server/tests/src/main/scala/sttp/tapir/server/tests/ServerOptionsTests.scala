package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.tests._

class ServerOptionsTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE],
    serverInterpreter: TestServerInterpreter[F, Any, OPTIONS, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._
  import serverInterpreter._

  def tests(): List[Test] = List(
    // we need at least two endpoints for the reject interceptor to be enabled
    testServer(
      "returns tapir-generated 404 when defaultHandlers(notFoundWhenRejected = true) is used",
      NonEmptyList.of(
        route(
          List(
            endpoint.in("p1").out(stringBody).serverLogic(_ => pureResult(s"ok1".asRight[Unit])),
            endpoint.in("p2").out(stringBody).serverLogic(_ => pureResult(s"ok2".asRight[Unit]))
          ),
          (_: CustomiseInterceptors[F, OPTIONS])
            .defaultHandlers(e => ValuedEndpointOutput(stringBody, "ERROR: " + e), notFoundWhenRejected = true)
        )
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/incorrect").send(backend).map { response =>
        response.code shouldBe StatusCode.NotFound
        response.body shouldBe Left("ERROR: Not Found")
      }
    }
  )
}
