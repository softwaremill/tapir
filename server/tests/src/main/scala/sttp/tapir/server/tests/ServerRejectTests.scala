package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.tests._

class ServerRejectTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE],
    serverInterpreter: TestServerInterpreter[F, Any, OPTIONS, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._
  import serverInterpreter._

  def tests(): List[Test] = List(
    testServer(
      "given a list of endpoints, should return 405 for unsupported methods",
      NonEmptyList.of(route(samePathEndpoints))
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/path").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.delete(uri"$baseUri/path").send(backend).map(_.code shouldBe StatusCode.MethodNotAllowed)
    },
    testServer(
      "given a list of endpoints with different paths, should return 405 for unsupported methods",
      NonEmptyList.of(route(differentPathEndpoints))
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/path1").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.delete(uri"$baseUri/path1").send(backend).map(_.code shouldBe StatusCode.MethodNotAllowed)
    },
    testServer(
      "given a list of endpoints and a customized reject handler, should return a custom response for unsupported methods",
      NonEmptyList.of(
        route(
          samePathEndpoints,
          (ci: CustomiseInterceptors[F, OPTIONS]) =>
            ci.rejectHandler(
              DefaultRejectHandler((_, _) => ValuedEndpointOutput(statusCode, StatusCode.BadRequest), None): DefaultRejectHandler[F]
            )
        )
      )
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/path").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.delete(uri"$baseUri/path").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    },
    testServer(endpoint.in("path"), "should return 404 for an unknown endpoint")((_: Unit) => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/path2").send(backend).map(_.code shouldBe StatusCode.NotFound)
    }
  )

  private val samePathEndpoints = List(
    endpoint.get.in("path").serverLogic((_: Unit) => pureResult(().asRight[Unit])),
    endpoint.post.in("path").serverLogic((_: Unit) => pureResult(().asRight[Unit]))
  )

  private val differentPathEndpoints = List(
    endpoint.get.in("path1").serverLogic((_: Unit) => pureResult(().asRight[Unit])),
    endpoint.post.in("path2").serverLogic((_: Unit) => pureResult(().asRight[Unit]))
  )
}
