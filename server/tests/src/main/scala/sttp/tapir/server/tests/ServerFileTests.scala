package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.monad.MonadError
import sttp.tapir.tests.Files.in_file_out_file
import sttp.tapir.tests.Test

import java.io.File

class ServerFileTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit m: MonadError[F]) {
  import createServerTest._

  def tests(): List[Test] =
    List(
      testServer(in_file_out_file)((file: File) => pureResult(file.asRight[Unit])) { (backend, baseUri) =>
        basicRequest
          .post(uri"$baseUri/api/echo")
          .body("pen pineapple apple pen")
          .send(backend)
          .map(_.body shouldBe Right("pen pineapple apple pen"))
      }
    )
}
