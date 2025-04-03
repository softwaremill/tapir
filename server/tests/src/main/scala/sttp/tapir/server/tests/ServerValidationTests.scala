package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir.tests.Validation._
import sttp.tapir.tests._
import sttp.tapir.tests.data._

class ServerValidationTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._

  def tests(): List[Test] = List(
    testServer(in_query_tagged, "support query validation with tagged type")((_: String) => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit=apple").send(backend).map(_.code shouldBe StatusCode.Ok) >>
          basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.code shouldBe StatusCode.BadRequest) >>
          basicRequest.get(uri"$baseUri?fruit=banana").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_query, "support query validation")((_: Int) => pureResult(().asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?amount=3").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri?amount=-3").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    },
    testServer(in_valid_json, "support jsonBody validation with wrapped type")((_: ValidFruitAmount) => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.post(uri"$baseUri").body("""{"fruit":"orange","amount":11}""").send(backend).map(_.code shouldBe StatusCode.Ok) >>
          basicRequest
            .post(uri"$baseUri")
            .body("""{"fruit":"orange","amount":0}""")
            .send(backend)
            .map(_.code shouldBe StatusCode.BadRequest) >>
          basicRequest.post(uri"$baseUri").body("""{"fruit":"orange","amount":1}""").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_valid_query, "support query validation with wrapper type")((_: IntWrapper) => pureResult(().asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?amount=11").send(backend).map(_.code shouldBe StatusCode.Ok) >>
          basicRequest.get(uri"$baseUri?amount=0").send(backend).map(_.code shouldBe StatusCode.BadRequest) >>
          basicRequest.get(uri"$baseUri?amount=1").send(backend).map(_.code shouldBe StatusCode.Ok)
    },
    testServer(in_valid_json_collection, "support jsonBody validation with list of wrapped type")((_: BasketOfFruits) =>
      pureResult(().asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest
        .post(uri"$baseUri")
        .body("""{"fruits":[{"fruit":"orange","amount":11}]}""")
        .send(backend)
        .map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.post(uri"$baseUri").body("""{"fruits": []}""").send(backend).map(_.code shouldBe StatusCode.BadRequest) >>
        basicRequest
          .post(uri"$baseUri")
          .body("""{fruits":[{"fruit":"orange","amount":0}]}""")
          .send(backend)
          .map(_.code shouldBe StatusCode.BadRequest)
    }
  )
}
