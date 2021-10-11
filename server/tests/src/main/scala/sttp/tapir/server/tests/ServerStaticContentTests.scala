package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests._

import java.io.File
import java.nio.file.{Files, StandardOpenOption}
import java.util.Comparator
import scala.concurrent.Future

class ServerStaticContentTests[F[_], ROUTE](
    serverInterpreter: TestServerInterpreter[F, Any, ROUTE],
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets],
    supportSettingContentLength: Boolean = true
) {

  def tests(): List[Test] = {
    val baseTests = List(
      Test("serve files from the given system path") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
            .use { port =>
              def get(path: List[String]) = basicRequest
                .get(uri"http://localhost:$port/$path")
                .response(asStringAlways)
                .send(backend)

              get("f1" :: Nil).map(_.body shouldBe "f1 content") >>
                get("f2" :: Nil).map(_.body shouldBe "f2 content") >>
                get("d1" :: "f3" :: Nil).map(_.body shouldBe "f3 content") >>
                get("d1" :: "d2" :: "f4" :: Nil).map(_.body shouldBe "f4 content")
            }
            .unsafeToFuture()
        }
      },
      Test("serve files from the given system path with a prefix") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("static" / "content")(testDir.getAbsolutePath))
            .use { port =>
              def get(path: List[String]) = basicRequest
                .get(uri"http://localhost:$port/$path")
                .response(asStringAlways)
                .send(backend)

              get("static" :: "content" :: "f1" :: Nil).map(_.body shouldBe "f1 content") >>
                get("static" :: "content" :: "d1" :: "f3" :: Nil).map(_.body shouldBe "f3 content")
            }
            .unsafeToFuture()
        }
      },
      Test("serve index.html when a directory is requested") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .get(uri"http://localhost:$port/d1")
                .response(asStringAlways)
                .send(backend)
                .map(_.body shouldBe "index content")
            }
            .unsafeToFuture()
        }
      },
      Test("Should return acceptRanges for head request") {
        withTestFilesDirectory { testDir =>
          val file = testDir.toPath.resolve("f1").toFile
          serveRoute(headServerEndpoint("test")(file.getAbsolutePath))
            .use { port =>
              basicRequest
                .head(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(r => {
                  r.code shouldBe StatusCode(200)
                  r.headers contains Header(HeaderNames.AcceptRanges, "bytes") shouldBe true
                  r.headers contains Header(HeaderNames.ContentLength, file.length().toString) shouldBe true
                })
            }
            .unsafeToFuture()
        }
      },
      Test("return 404 when files are not found") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=0-1"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.headers contains Header(HeaderNames.AcceptRanges, "bytes") shouldBe true)
            }
            .unsafeToFuture()
        }
      },
      Test("should return whole while file if header not present ") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.body shouldBe "f1 content")

            }
            .unsafeToFuture()
        }
      },
      Test("returns 200 status code for whole file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.code shouldBe StatusCode.Ok)

            }
            .unsafeToFuture()
        }
      },
      Test("should return 416 if over range") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=0-11"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.code shouldBe StatusCode.RangeNotSatisfiable)
            }
            .unsafeToFuture()
        }
      },
      Test("returns content range header with matching bytes") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=1-3"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.headers contains Header(HeaderNames.ContentRange, "bytes 1-3/10") shouldBe true)
            }
            .unsafeToFuture()
        }
      },
      Test("returns 206 status code for partial content") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=1-3"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.code shouldBe StatusCode.PartialContent)
            }
            .unsafeToFuture()
        }
      },
      Test("should return bytes 4-7 from file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=4-7"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.body shouldBe "onte")
            }
            .unsafeToFuture()
        }
      },
      Test("should return bytes 100000-200000 from file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f5").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=100000-200000"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map { r =>
                  r.body.length shouldBe 100001
                  r.body.head shouldBe 'x'
                }
            }
            .unsafeToFuture()
        }
      },
      Test("should return last 200000 bytes from file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f5").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=100000-"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map { r =>
                  r.body.length shouldBe 200000
                  r.body.head shouldBe 'x'
                }
            }
            .unsafeToFuture()
        }
      },
      Test("should return last 100000 bytes from file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f5").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=-100000"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map { r =>
                  r.body.length shouldBe 100000
                  r.body.head shouldBe 'x'
                }
            }
            .unsafeToFuture()
        }
      },
      Test("should fail for incorrect range") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F]("test")(testDir.toPath.resolve("f5").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=-"))
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map { _.code shouldBe StatusCode(400) }
            }
            .unsafeToFuture()
        }
      },
      Test("if an etag is present, only return the file if it doesn't match the etag") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
            .use { port =>
              def get(etag: Option[String]) = basicRequest
                .get(uri"http://localhost:$port/f1")
                .header(HeaderNames.IfNoneMatch, etag)
                .response(asStringAlways)
                .send(backend)

              get(None).flatMap { r1 =>
                r1.code shouldBe StatusCode.Ok
                val etag = r1.header(HeaderNames.Etag).get

                get(Some(etag)).map { r2 =>
                  r2.code shouldBe StatusCode.NotModified
                } >> get(Some(etag.replace("-", "-x"))).map { r2 =>
                  r2.code shouldBe StatusCode.Ok
                }
              }
            }
            .unsafeToFuture()
        }
      },
      Test("return file metadata") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .get(uri"http://localhost:$port/img.gif")
                .response(asStringAlways)
                .send(backend)
                .map { r =>
                  r.contentLength shouldBe Some(11)
                  r.contentType shouldBe Some(MediaType.ImageGif.toString())
                  r.header(HeaderNames.LastModified)
                    .flatMap(t => Header.parseHttpDate(t).toOption)
                    .map(_.toEpochMilli)
                    .get should be > (System.currentTimeMillis() - 10000L)
                  r.header(HeaderNames.Etag).isDefined shouldBe true
                }
            }
            .unsafeToFuture()
        }
      },
      Test("serve a single resource") {
        serveRoute(resourceServerEndpoint[F](emptyInput)(classOf[ServerStaticContentTests[F, ROUTE]].getClassLoader, "test/r1.txt"))
          .use { port =>
            basicRequest
              .get(uri"http://localhost:$port/$path")
              .response(asStringAlways)
              .send(backend)
              .map(_.body shouldBe "Resource 1")
          }
          .unsafeToFuture()
      },
      Test("not return a resource outside of the resource prefix directory") {
        serveRoute(resourcesServerEndpoint[F](emptyInput)(classOf[ServerStaticContentTests[F, ROUTE]].getClassLoader, "test"))
          .use { port =>
            basicRequest
              .get(uri"http://localhost:$port/../test2/r5.txt")
              .response(asStringAlways)
              .send(backend)
              .map(_.body should not be "Resource 5")
          }
          .unsafeToFuture()
      },
      Test("serve resources") {
        serveRoute(resourcesServerEndpoint[F](emptyInput)(classOf[ServerStaticContentTests[F, ROUTE]].getClassLoader, "test"))
          .use { port =>
            def get(path: List[String]) = basicRequest
              .get(uri"http://localhost:$port/$path")
              .response(asStringAlways)
              .send(backend)

            get("r1.txt" :: Nil).map(_.body shouldBe "Resource 1") >>
              get("r2.txt" :: Nil).map(_.body shouldBe "Resource 2") >>
              get("d1/r3.txt" :: Nil).map(_.body shouldBe "Resource 3") >>
              get("d1/d2/r4.txt" :: Nil).map(_.body shouldBe "Resource 4")
          }
          .unsafeToFuture()
      },
      Test("not return a file outside of the system path") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath + "/d1"))
            .use { port =>
              basicRequest
                .get(uri"http://localhost:$port/../f1")
                .response(asStringAlways)
                .send(backend)
                .map(_.body should not be "f1 content")
            }
            .unsafeToFuture()
        }
      },
      Test("return 404 when a resource is not found") {
        serveRoute(resourcesServerEndpoint[F](emptyInput)(classOf[ServerStaticContentTests[F, ROUTE]].getClassLoader, "test"))
          .use { port =>
            basicRequest
              .get(uri"http://localhost:$port/r3")
              .response(asStringAlways)
              .send(backend)
              .map(_.code shouldBe StatusCode.NotFound)
          }
          .unsafeToFuture()
      },
      Test("serve a single file from the given system path") {
        withTestFilesDirectory { testDir =>
          serveRoute(fileServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port =>
              basicRequest
                .get(uri"http://localhost:$port/test")
                .response(asStringAlways)
                .send(backend)
                .map(_.body shouldBe "f1 content")
            }
            .unsafeToFuture()
        }
      },
      Test("if an etag is present, only return the resource if it doesn't match the etag") {
        serveRoute(resourcesServerEndpoint[F](emptyInput)(classOf[ServerStaticContentTests[F, ROUTE]].getClassLoader, "test"))
          .use { port =>
            def get(etag: Option[String]) = basicRequest
              .get(uri"http://localhost:$port/r1.txt")
              .header(HeaderNames.IfNoneMatch, etag)
              .response(asStringAlways)
              .send(backend)

            get(None).flatMap { r1 =>
              r1.code shouldBe StatusCode.Ok
              val etag = r1.header(HeaderNames.Etag).get

              get(Some(etag)).map { r2 =>
                r2.code shouldBe StatusCode.NotModified
              } >> get(Some(etag.replace("-", "-x"))).map { r2 =>
                r2.code shouldBe StatusCode.Ok
              }
            }
          }
          .unsafeToFuture()
      }
    )
    val resourceMetadataTest = Test("return resource metadata") {
      serveRoute(resourcesServerEndpoint[F](emptyInput)(classOf[ServerStaticContentTests[F, ROUTE]].getClassLoader, "test"))
        .use { port =>
          basicRequest
            .get(uri"http://localhost:$port/r1.txt")
            .response(asStringAlways)
            .send(backend)
            .map { r =>
              r.contentLength shouldBe Some(10)
              r.contentType shouldBe Some(MediaType.TextPlain.toString())
              r.header(HeaderNames.LastModified)
                .flatMap(t => Header.parseHttpDate(t).toOption)
                .map(_.toEpochMilli)
                .get should be > (1629180000000L) // 8:00 17 Aug 2021 when the test was written
              r.header(HeaderNames.Etag).isDefined shouldBe true
            }
        }
        .unsafeToFuture()
    }
    if (supportSettingContentLength) baseTests :+ resourceMetadataTest
    else baseTests
  }

  def serveRoute[I, E, O](e: ServerEndpoint[I, E, O, Any, F]): Resource[IO, Port] =
    serverInterpreter.server(NonEmptyList.of(serverInterpreter.route(e)))

  def withTestFilesDirectory[T](t: File => Future[T]): Future[T] = {
    val parent = Files.createTempDirectory("tapir-tests")

    parent.resolve("d1/d2").toFile.mkdirs()

    Files.write(parent.resolve("f1"), "f1 content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("f2"), "f2 content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("img.gif"), "img content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("d1/f3"), "f3 content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("d1/index.html"), "index content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("d1/d2/f4"), "f4 content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("f5"), ("x" * 300000).getBytes, StandardOpenOption.CREATE_NEW)
    parent.toFile.deleteOnExit()

    import scala.concurrent.ExecutionContext.Implicits.global
    val result = t(parent.toFile)
    result.onComplete(_ =>
      Files
        .walk(parent)
        .sorted(Comparator.reverseOrder())
        .map[File](f => f.toFile)
        .forEach(f => { val _ = f.delete })
    )

    result
  }
}
