package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3.{multipartFile, _}
import sttp.model.{Part, StatusCode}
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.tests.Multipart._
import sttp.tapir.tests.TestUtil.{readFromFile, writeToFile}
import sttp.tapir.tests.data.{DoubleFruit, FruitAmount, FruitData}
import sttp.tapir.tests.{MultipleFileUpload, Test, data}
import sttp.tapir.server.model.EndpointExtensions._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import java.io.File
import fs2.io.file.Files
import cats.effect.IO
import fs2.io.file.Path

class ServerMultipartTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE],
    partContentTypeHeaderSupport: Boolean = true,
    partOtherHeaderSupport: Boolean = true,
    maxContentLengthSupport: Boolean = true,
    chunkingSupport: Boolean = true,
    multipartResponsesSupport: Boolean = true
)(implicit m: MonadError[F]) {
  import createServerTest._

  def tests(): List[Test] =
    basicTests() ++ (if (partContentTypeHeaderSupport) contentTypeHeaderTests() else Nil) ++
      (if (maxContentLengthSupport) maxContentLengthTests() else Nil) ++ (if (multipartResponsesSupport) multipartResponsesTests()
                                                                          else
                                                                            Nil) ++ (if (chunkingSupport) chunkedMultipartTests() else Nil)

  def maxContentLengthTests(): List[Test] = List(
    testServer(
      endpoint.post
        .in("api" / "echo" / "multipart")
        .in(multipartBody[DoubleFruit])
        .out(stringBody)
        .maxRequestBodyLength(15000),
      "multipart with maxContentLength"
    )((df: DoubleFruit) => pureResult(("ok").asRight[Unit])) { (backend, baseUri) =>
      basicStringRequest
        .post(uri"$baseUri/api/echo/multipart")
        .multipartBody(multipart("fruitA", "pineapple".repeat(1100)), multipart("fruitB", "maracuja".repeat(1200)))
        .send(backend)
        .map { r =>
          r.code shouldBe StatusCode.PayloadTooLarge
        } >> basicStringRequest
        .post(uri"$baseUri/api/echo/multipart")
        .multipartBody(multipart("fruitA", "pineapple".repeat(850)), multipart("fruitB", "maracuja".repeat(850)))
        .send(backend)
        .map { r =>
          r.code shouldBe StatusCode.Ok
        }
    }
  )

  def basicTests(): List[Test] = {
    List(
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
      },
      testServer(in_raw_multipart_out_string, "boundary substring in body")((parts: Seq[Part[Array[Byte]]]) =>
        pureResult(
          parts.map(part => s"${part.name}:${new String(part.body)}").mkString("\n__\n").asRight[Unit]
        )
      ) { (backend, baseUri) =>
        val testBody = "--AAB\r\n" +
          "Content-Disposition: form-data; name=\"firstPart\"\r\n" +
          "Content-Type: text/plain\r\n" +
          "\r\n" +
          "BODYONE\r\n" +
          "--AA\r\n" +
          "--AAB\r\n" +
          "Content-Disposition: form-data; name=\"secondPart\"\r\n" +
          "Content-Type: text/plain\r\n" +
          "\r\n" +
          "BODYTWO\r\n" +
          "--AAB--\r\n"
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .header("Content-Type", "multipart/form-data; boundary=AAB")
          .body(testBody)
          .send(backend)
          .map { r =>
            r.code shouldBe StatusCode.Ok
            r.body should be("firstPart:BODYONE\r\n--AA\n__\nsecondPart:BODYTWO")
          }
      },
      testServer(in_file_multipart_out_string, "simple file multipart body")((fd: FruitData) => {
        val content = Await.result(readFromFile(fd.data.body), 3.seconds)
        pureResult(content.reverse.asRight[Unit])
      }) { (backend, baseUri) =>
        val file = writeToFile("peach2 mario2")
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .multipartBody(multipartFile("data", file).fileName("fruit-data7.txt").header("X-Auth", "12Aa"))
          .send(backend)
          .map { r =>
            r.code shouldBe StatusCode.Ok
            r.body shouldBe "2oiram 2hcaep"
          }
      },
      testServer(in_file_multipart_out_string, "large file multipart body")((fd: FruitData) => {
        val fileSize = fd.data.body.length() // FIXME is 0, because decoder.destroy() removes the file
        pureResult(fileSize.toString.asRight[Unit])
      }) { (backend, baseUri) =>
        val file = File.createTempFile("test", "tapir")
        file.deleteOnExit()
        fs2.Stream
          .constant[IO, Byte]('x')
          .take(5 * 1024 * 1024)
          .through(Files.forAsync[IO].writeAll(Path.fromNioPath(file.toPath)))
          .compile
          .drain >>
          basicStringRequest
            .post(uri"$baseUri/api/echo/multipart")
            .multipartBody(multipartFile("data", file).fileName("fruit-data8.txt"))
            .send(backend)
            .map { r =>
              r.code shouldBe StatusCode.Ok
              r.body shouldBe "5242880"
            }
      },
      testServer(in_file_multipart_out_string, "file from a multipart attribute")((fd: FruitData) => {
        val content = Await.result(readFromFile(fd.data.body), 3.seconds)
        pureResult(content.reverse.asRight[Unit])
      }) { (backend, baseUri) =>
        basicStringRequest
          .post(uri"$baseUri/api/echo/multipart")
          .multipartBody(multipart("data", "peach3 mario3"))
          .send(backend)
          .map { r =>
            // r.code shouldBe StatusCode.Ok
            r.body shouldBe "3oiram 3hcaep"
          }
      }
    )
  }

  def chunkedMultipartTests() = List(
    testServer(in_raw_multipart_out_string, "chunked multipart attribute")((parts: Seq[Part[Array[Byte]]]) =>
      pureResult(
        parts.map(part => s"${part.name}:${new String(part.body)}").mkString("\n__\n").asRight[Unit]
      )
    ) { (backend, baseUri) =>
      val testBody = "61\r\n--boundary123\r\n" +
        "Content-Disposition: form-data; name=\"attr1\"\r\n" +
        "Content-Type: text/plain\r\n" +
        "\r\nValue1\r\n" +
        "\r\n47\r\n--boundary123\r\n" +
        "Content-Disposition: form-data; name=\"attr2\"\r\n" +
        "\r\nPart1 of\r\n" +
        "1E\r\n Attr2 Value\r\n" +
        "--boundary123--\r\n\r\n" +
        "0\r\n\r\n"
      basicStringRequest
        .post(uri"$baseUri/api/echo/multipart")
        .header("Content-Type", "multipart/form-data; boundary=boundary123")
        .header("Transfer-Encoding", "chunked")
        .body(testBody)
        .send(backend)
        .map { r =>
          r.code shouldBe StatusCode.Ok
          r.body should be("attr1:Value1\n__\nattr2:Part1 of Attr2 Value")
        }
    }
  )

  def multipartResponsesTests() = List(
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
          if (partOtherHeaderSupport) r.body should include regex "((?i)X-Auth):[ ]?Some\\(12Aa\\)"
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
            r.body should include regex "((?i)X-Auth):[ ]?12Aax"
            r.body should include regex "((?i)X-Auth):[ ]?12Abx"
            r.body should include regex "((?i)X-Auth):[ ]?12Acx"
          }
          r.body should include("peach mario 1 result")
          r.body should include("peach mario 2 result")
          r.body should include("peach mario 3 result")
        }
    }
  )

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
          r.body.toLowerCase() should include regex "content-type:[ ]?text/html"
        }
    }
  )
}
