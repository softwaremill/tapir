package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3.{basicRequest, multipartFile, _}
import sttp.model.{Part, StatusCode}
import sttp.monad.MonadError
import sttp.tapir.tests.TestUtil.{readFromFile, writeToFile}
import sttp.tapir.tests.{
  FruitAmount,
  FruitData,
  Test,
  in_file_multipart_out_multipart,
  in_file_out_file,
  in_raw_multipart_out_string,
  in_simple_multipart_out_multipart
}

import java.io.File
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerFileMultipartTests[F[_], ROUTE](
    createServerTest: CreateServerTest[F, Any, ROUTE],
    multipartInlineHeaderSupport: Boolean = true
)(implicit m: MonadError[F]) {
  import createServerTest._

  private val basicStringRequest = basicRequest.response(asStringAlways)
  private def pureResult[T](t: T): F[T] = m.unit(t)

  def tests(): List[Test] =
    basicTests() ++ (if (multipartInlineHeaderSupport) multipartInlineHeaderTests() else Nil)

  def basicTests(): List[Test] = {
    List(
      testServer(in_file_out_file)((file: File) => pureResult(file.asRight[Unit])) { (backend, baseUri) =>
        basicRequest
          .post(uri"$baseUri/api/echo")
          .body("pen pineapple apple pen")
          .send(backend)
          .map(_.body shouldBe Right("pen pineapple apple pen"))
      },
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
      testServer(in_file_multipart_out_multipart)((fd: FruitData) =>
        pureResult(
          FruitData(
            Part("", writeToFile(Await.result(readFromFile(fd.data.body), 3.seconds).reverse), fd.data.otherDispositionParams, Nil)
              .header("X-Auth", fd.data.headers.find(_.is("X-Auth")).map(_.value).toString)
          ).asRight[Unit]
        )
      ) { (backend, baseUri) =>
        val file = writeToFile("peach mario")
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .multipartBody(multipartFile("data", file).fileName("fruit-data.txt").header("X-Auth", "12Aa"))
          .send(backend)
          .map { r =>
            r.code shouldBe StatusCode.Ok
            if (multipartInlineHeaderSupport) r.body should include regex "X-Auth: Some\\(12Aa\\)"
            r.body should include regex "name=\"data\"[\\s\\S]*oiram hcaep"
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

  def multipartInlineHeaderTests(): List[Test] = List(
    testServer(in_file_multipart_out_multipart, "with part content type header")((fd: FruitData) =>
      pureResult(
        FruitData(
          Part("", fd.data.body, fd.data.otherDispositionParams, fd.data.headers)
        ).asRight[Unit]
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
