package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests._

class Server405Tests[F[_], ROUTE, B](
    createServerTest: CreateServerTest[F, Any, ROUTE, B]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._

  private def pureResult[T](t: T): F[T] = m.unit(t)

  def tests(): List[Test] = List(
    testServer(endpoint.in("path"), "request an unknown endpoint")((_: Unit) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(baseUri).send(backend).map(_.code shouldBe StatusCode.NotFound)
    },
    testServer(endpoint.get, "request a known endpoint with incorrect method")((_: Unit) => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.post(baseUri).send(backend).map(_.code shouldBe StatusCode.MethodNotAllowed)
    },
    testServer(endpoint.get.in("path"), "request an unknown endpoint with incorrect method")((_: Unit) => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.post(baseUri).send(backend).map(_.code shouldBe StatusCode.NotFound)
    }
  )
}
