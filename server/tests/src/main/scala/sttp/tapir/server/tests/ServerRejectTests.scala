package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests._

class ServerRejectTests[F[_], ROUTE](
    createServerTest: CreateServerTest[F, Any, ROUTE],
    serverInterpreter: TestServerInterpreter[F, Any, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._
  import serverInterpreter._

  def tests(): List[Test] = List(
    testServer(
      "given a list of endpoints, should return 405 for unsupported methods",
      NonEmptyList.of(
        route(
          List(
            endpoint.get.in("path").serverLogic((_: Unit) => pureResult(().asRight[Unit])),
            endpoint.post.in("path").serverLogic((_: Unit) => pureResult(().asRight[Unit]))
          )
        )
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/path").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.delete(uri"$baseUri/path").send(backend).map(_.code shouldBe StatusCode.MethodNotAllowed)
    },
    testServer(endpoint.in("path"), "should return 404 for an unknown endpoint")((_: Unit) => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/path2").send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    testServer(endpoint.get.in("path"), "should return 404 for an unsupported method, when a single endpoint is interpreted")((_: Unit) =>
      pureResult(().asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.post(uri"$baseUri/path").send(backend).map(_.code shouldBe StatusCode.NotFound)
    }
  )
}
