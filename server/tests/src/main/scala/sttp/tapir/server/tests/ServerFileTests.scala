package sttp.tapir.server.tests

import cats.effect.IO
import cats.implicits._
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests.Files.in_file_out_file
import sttp.tapir.tests.Test

import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters.asScalaSetConverter

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
      }, {
        val files = ConcurrentHashMap.newKeySet[TapirFile]()
        testServer(endpoint.post.in("api" / "echo").in(fileBody).out(stringBody), "file deleted after request handling completes")(
          (file: File) => {
            files.add(file)
            file.exists() shouldBe true
            pureResult("ok".asRight[Unit])
          }
        ) { (backend, baseUri) =>
          val sendRequest = basicRequest
            .post(uri"$baseUri/api/echo")
            .body("pen pineapple apple pen")
            .send(backend)
            .map(_.code shouldBe StatusCode.Ok)
          val checkFiles = IO.blocking {
            eventually { // file cleanup might run asynchronously to completing the request
              files.asScala.foreach { file =>
                if (Files.exists(file.toPath)) {
                  fail(s"File ${file.getName} still exists")
                }
              }
              succeed
            }
          }
          sendRequest >> checkFiles
        }
      }
    )
}
