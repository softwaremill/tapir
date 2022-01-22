package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3.{multipartFile, _}
import sttp.model.{Part, StatusCode}
import sttp.monad.MonadError
import sttp.tapir.tests.Multipart.{
  in_file_list_multipart_out_multipart,
  in_file_multipart_out_multipart,
  in_raw_multipart_out_string,
  in_simple_multipart_out_multipart,
  in_simple_multipart_out_string
}
import sttp.tapir.tests.TestUtil.{readFromFile, writeToFile}
import sttp.tapir.tests.data.{FruitAmount, FruitData}
import sttp.tapir.tests.{MultipleFileUpload, Test, data}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerMultipartTests[F[_], ROUTE](
    createServerTest: CreateServerTest[F, Any, ROUTE],
    partContentTypeHeaderSupport: Boolean = true,
    partOtherHeaderSupport: Boolean = true
)(implicit m: MonadError[F]) {
  import createServerTest._

  def tests(): List[Test] =
    basicTests() ++ (if (partContentTypeHeaderSupport) contentTypeHeaderTests() else Nil)

  def basicTests(): List[Test] = {
    List(
      testServer(in_simple_multipart_out_multipart)((fa: FruitAmount) =>
        pureResult(FruitAmount(fa.fruit + " apple", fa.amount * 2).asRight[Unit])
      ) { (backend, baseUri) =>
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .multipartBody(multipart("fruit", "pineapple"), multipart("amount", "120"))
          .send(backend)
          .map { r =>
            r.body should include regex "name=\"fruit\"[\\s\\S]*pineapple apple"
            r.body should include regex "name=\"amount\"[\\s\\S]*240"
          }
      },
      testServer(in_simple_multipart_out_string, "discard unknown parts")((fa: FruitAmount) => pureResult(fa.toString.asRight[Unit])) {
        (backend, baseUri) =>
          basicStringRequest
            .post(uri"$baseUri/api/echo/multipart")
            .multipartBody(multipart("fruit", "pineapple"), multipart("amount", "120"), multipart("shape", "regular"))
            .send(backend)
            .map { r =>
              r.body shouldBe "FruitAmount(pineapple,120)"
            }
      },
      testServer(in_file_multipart_out_multipart)((fd: FruitData) =>
        pureResult(
          data
            .FruitData(
              Part("", writeToFile(Await.result(readFromFile(fd.data.body), 3.seconds).reverse), fd.data.otherDispositionParams, Nil)
                .header("X-Auth", fd.data.headers.find(_.is("X-Auth")).map(_.value).toString)
            )
            .asRight[Unit]
        )
      ) { (backend, baseUri) =>
        val file = writeToFile("peach mario")
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .multipartBody(multipartFile("data", file).fileName("fruit-data.txt").header("X-Auth", "12Aa"))
          .send(backend)
          .map { r =>
            r.code shouldBe StatusCode.Ok
            if (partOtherHeaderSupport) r.body should include regex "X-Auth: Some\\(12Aa\\)"
            r.body should include regex "name=\"data\"[\\s\\S]*oiram hcaep"
          }
      },
      testServer(in_file_list_multipart_out_multipart) { (mfu: MultipleFileUpload) =>
        val files = mfu.files.map { part =>
          Part(
            part.name,
            writeToFile(Await.result(readFromFile(part.body), 3.seconds) + " result"),
            part.otherDispositionParams,
            Nil
          ).header("X-Auth", part.headers.find(_.is("X-Auth")).map(_.value + "x").getOrElse(""))
        }
        pureResult(MultipleFileUpload(files).asRight[Unit])
      } { (backend, baseUri) =>
        val file1 = writeToFile("peach mario 1")
        val file2 = writeToFile("peach mario 2")
        val file3 = writeToFile("peach mario 3")
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .multipartBody(
            multipartFile("files", file1).fileName("fruit-data-1.txt").header("X-Auth", "12Aa"),
            multipartFile("files", file2).fileName("fruit-data-2.txt").header("X-Auth", "12Ab"),
            multipartFile("files", file3).fileName("fruit-data-3.txt").header("X-Auth", "12Ac")
          )
          .send(backend)
          .map { r =>
            r.code shouldBe StatusCode.Ok
            if (partOtherHeaderSupport) {
              r.body should include("X-Auth: 12Aax")
              r.body should include("X-Auth: 12Abx")
              r.body should include("X-Auth: 12Acx")
            }
            r.body should include("peach mario 1 result")
            r.body should include("peach mario 2 result")
            r.body should include("peach mario 3 result")
          }
      },
      testServer(in_raw_multipart_out_string)((parts: Seq[Part[Array[Byte]]]) =>
        pureResult(
          parts.map(part => s"${part.name}:${new String(part.body)}").mkString("\n").asRight[Unit]
        )
      ) { (backend, baseUri) =>
        val file1 = writeToFile("peach mario")
        val file2 = writeToFile("daisy luigi")
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .multipartBody(
            multipartFile("file1", file1).fileName("file1.txt"),
            multipartFile("file2", file2).fileName("file2.txt")
          )
          .send(backend)
          .map { r =>
            r.code shouldBe StatusCode.Ok
            r.body should include("file1:peach mario")
            r.body should include("file2:daisy luigi")
          }
      }
    )
  }

  def contentTypeHeaderTests(): List[Test] = List(
    testServer(in_file_multipart_out_multipart, "with part content type header")((fd: FruitData) =>
      pureResult(
        data
          .FruitData(
            Part("", fd.data.body, fd.data.otherDispositionParams, fd.data.headers)
          )
          .asRight[Unit]
      )
    ) { (backend, baseUri) =>
      val file = writeToFile("peach mario")
      basicStringRequest
        .post(uri"$baseUri/api/echo/multipart")
        .multipartBody(multipartFile("data", file).contentType("text/html"))
        .send(backend)
        .map { r =>
          r.code shouldBe StatusCode.Ok
          r.body.toLowerCase() should include("content-type: text/html")
        }
    }
  )
}
