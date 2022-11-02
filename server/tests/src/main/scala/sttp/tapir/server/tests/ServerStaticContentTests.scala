package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.static.{FilesOptions, ResourcesOptions}
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.tests._

import java.io.File
import java.nio.file.{Files, StandardOpenOption}
import java.util.Comparator
import scala.concurrent.Future

class ServerStaticContentTests[F[_], OPTIONS, ROUTE](
    serverInterpreter: TestServerInterpreter[F, Any, OPTIONS, ROUTE],
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets],
    supportSettingContentLength: Boolean = true
) {

  private def get(port: Int, path: List[String]): IO[Response[String]] = basicRequest
    .get(uri"http://localhost:$port/$path")
    .response(asStringAlways)
    .send(backend)

  private def head(port: Int, path: List[String]): IO[Response[String]] = basicRequest
    .head(uri"http://localhost:$port/$path")
    .response(asStringAlways)
    .send(backend)

  private val classLoader = classOf[ServerStaticContentTests[F, OPTIONS, ROUTE]].getClassLoader

  def tests(): List[Test] = {
    val baseTests = List(
      Test("should serve files from the given system path") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
            .use { port =>
              get(port, "f1" :: Nil).map(_.body shouldBe "f1 content") >>
                get(port, "f2" :: Nil).map(_.body shouldBe "f2 content") >>
                get(port, "d1" :: "f3" :: Nil).map(_.body shouldBe "f3 content") >>
                get(port, "d1" :: "d2" :: "f4" :: Nil).map(_.body shouldBe "f4 content")
            }
            .unsafeToFuture()
        }
      },
      Test("should serve files from the given system path with a prefix") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("static" / "content")(testDir.getAbsolutePath))
            .use { port =>
              get(port, "static" :: "content" :: "f1" :: Nil).map(_.body shouldBe "f1 content") >>
                get(port, "static" :: "content" :: "d1" :: "f3" :: Nil).map(_.body shouldBe "f3 content")
            }
            .unsafeToFuture()
        }
      },
      Test("should serve index.html when a directory is requested") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
            .use { port =>
              get(port, List("d1")).map(_.body shouldBe "index content")
            }
            .unsafeToFuture()
        }
      },
      Test("should serve files from the given system path with filter") {
        withTestFilesDirectory { testDir =>
          serveRoute(
            filesGetServerEndpoint[F](emptyInput)(testDir.getAbsolutePath, FilesOptions.default.fileFilter(_.exists(_.contains("2"))))
          )
            .use { port =>
              get(port, "f1" :: Nil).map(_.code shouldBe StatusCode.NotFound) >>
                get(port, "f2" :: Nil).map(_.body shouldBe "f2 content") >>
                get(port, "d1" :: "f3" :: Nil).map(_.code shouldBe StatusCode.NotFound) >>
                get(port, "d1" :: "d2" :: "f4" :: Nil).map(_.body shouldBe "f4 content")
            }
            .unsafeToFuture()
        }
      },
      Test("should return acceptRanges for head request") {
        withTestFilesDirectory { testDir =>
          val file = testDir.toPath.resolve("f1").toFile
          serveRoute(filesHeadServerEndpoint("test")(testDir.getAbsolutePath))
            .use { port =>
              head(port, List("test", "f1"))
                .map { r =>
                  r.code shouldBe StatusCode.Ok
                  r.headers contains Header(HeaderNames.AcceptRanges, ContentRangeUnits.Bytes) shouldBe true
                  r.headers contains Header(HeaderNames.ContentLength, file.length().toString) shouldBe true
                }
            }
            .unsafeToFuture()
        }
      },
      Test("should create head and get endpoints") {
        withTestFilesDirectory { testDir =>
          val file = testDir.toPath.resolve("f2").toFile
          val headAndGetEndpoint = filesServerEndpoints[F]("test")(testDir.getAbsolutePath)
          serverInterpreter
            .server(NonEmptyList.of(serverInterpreter.route(headAndGetEndpoint)))
            .use { port =>
              head(port, List("test", "f2"))
                .map { r =>
                  r.code shouldBe StatusCode.Ok
                  r.headers contains Header(HeaderNames.AcceptRanges, ContentRangeUnits.Bytes) shouldBe true
                  r.headers contains Header(HeaderNames.ContentLength, file.length().toString) shouldBe true
                } >>
                basicRequest
                  .headers(Header(HeaderNames.Range, "bytes=3-6"))
                  .get(uri"http://localhost:$port/test/f2")
                  .response(asStringAlways)
                  .send(backend)
                  .map(r => {
                    r.body shouldBe "cont"
                    r.code shouldBe StatusCode.PartialContent
                    r.body.length shouldBe 4
                    r.headers contains Header(HeaderNames.ContentRange, "bytes 3-6/10") shouldBe true
                  })
            }
            .unsafeToFuture()
        }
      },
      Test("should return 404 when files are not found") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port => get(port, List("test")).map(_.code shouldBe StatusCode.NotFound) }
            .unsafeToFuture()
        }
      },
      Test("should return 404 for HEAD request and not existing file ") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesHeadServerEndpoint(emptyInput)(testDir.getAbsolutePath))
            .use { port => head(port, List("test")).map(_.code shouldBe StatusCode.NotFound) }
            .unsafeToFuture()
        }
      },
      Test("should return default file when file is not found") {
        withTestFilesDirectory { testDir =>
          serveRoute(
            filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath, FilesOptions.default.defaultFile(List("d1", "index.html")))
          )
            .use { port =>
              get(port, List("test", "f10")).map(_.body shouldBe "index content") >>
                get(port, List("test", "f1")).map(_.body shouldBe "f1 content")
            }
            .unsafeToFuture()
        }
      },
      Test("should return whole while file if range header not present") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port => get(port, List("test", "f2")).map(_.body shouldBe "f2 content") }
            .unsafeToFuture()
        }
      },
      Test("should return 200 status code for whole file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port => get(port, List("test", "f2")).map(_.code shouldBe StatusCode.Ok) }
            .unsafeToFuture()
        }
      },
      Test("should return 416 if over range") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=0-11"))
                .get(uri"http://localhost:$port/test/f2")
                .response(asStringAlways)
                .send(backend)
                .map(_.code shouldBe StatusCode.RangeNotSatisfiable)
            }
            .unsafeToFuture()
        }
      },
      Test("should return content range header with matching bytes") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=1-3"))
                .get(uri"http://localhost:$port/test/f1")
                .response(asStringAlways)
                .send(backend)
                .map(_.headers contains Header(HeaderNames.ContentRange, "bytes 1-3/10") shouldBe true)
            }
            .unsafeToFuture()
        }
      },
      Test("should return 206 status code for partial content") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=1-3"))
                .get(uri"http://localhost:$port/test/f1")
                .response(asStringAlways)
                .send(backend)
                .map(_.code shouldBe StatusCode.PartialContent)
            }
            .unsafeToFuture()
        }
      },
      Test("should return bytes 4-7 from file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=4-7"))
                .get(uri"http://localhost:$port/test/f1")
                .response(asStringAlways)
                .send(backend)
                .map(_.body shouldBe "onte")
            }
            .unsafeToFuture()
        }
      },
      Test("should return bytes 100000-200000 from file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=100000-200000"))
                .get(uri"http://localhost:$port/test/f5")
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
      Test("should return bytes from 100000 from file") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=100000-"))
                .get(uri"http://localhost:$port/test/f5")
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
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=-100000"))
                .get(uri"http://localhost:$port/test/f5")
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
          serveRoute(filesGetServerEndpoint[F]("test")(testDir.getAbsolutePath))
            .use { port =>
              basicRequest
                .headers(Header(HeaderNames.Range, "bytes=-"))
                .get(uri"http://localhost:$port/test/f5")
                .response(asStringAlways)
                .send(backend)
                .map { _.code shouldBe StatusCode.BadRequest }
            }
            .unsafeToFuture()
        }
      },
      Test("if an etag is present, should only return the file if it doesn't match the etag") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
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
      Test("should return file metadata") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))
            .use { port =>
              get(port, List("img.gif")) map { r =>
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
      Test("should not return a file outside of the system path") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F](emptyInput)(testDir.getAbsolutePath + "/d1"))
            .use { port => get(port, List("..", "f1")).map(_.body should not be "f1 content") }
            .unsafeToFuture()
        }
      },
      Test("should not return a file outside of the system path, when the path is given as a single segment") {
        withTestFilesDirectory { testDir =>
          serveRoute(filesGetServerEndpoint[F](emptyInput)(testDir.getAbsolutePath + "/d1"))
            .use { port => get(port, List("../f1")).map(_.body should not be "f1 content") }
            .unsafeToFuture()
        }
      },
      Test("should serve a single resource") {
        serveRoute(resourceGetServerEndpoint[F](emptyInput)(classLoader, "test/r1.txt"))
          .use { port => get(port, Nil).map(_.body shouldBe "Resource 1") }
          .unsafeToFuture()
      },
      Test("should serve single gzipped resource") {
        serveRoute(resourceGetServerEndpoint[F](emptyInput)(classLoader, "test/r3.txt", ResourcesOptions.default.withUseGzippedIfAvailable))
          .use { port =>
            emptyRequest
              .acceptEncoding("gzip")
              .get(uri"http://localhost:$port")
              .response(asStringAlways)
              .send(backend)
              .map(r => {
                r.code shouldBe StatusCode.Ok
                r.body shouldBe "Gzipped resource"
                r.headers contains Header(HeaderNames.ContentEncoding, "gzip") shouldBe true
                r.headers contains Header(HeaderNames.ContentType, MediaType.ApplicationGzip.toString()) shouldBe true
              })
          }
          .unsafeToFuture()
      },
      Test("should return 404 for resources without extension") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "test"))
          .use { port =>
            emptyRequest
              .get(uri"http://localhost:$port/r3")
              .response(asStringAlways)
              .send(backend)
              .map(_.code shouldBe StatusCode.NotFound)
          }
          .unsafeToFuture()
      },
      Test("should serve resource at path for preGzipped endpoint without correct header") {
        serveRoute(resourceGetServerEndpoint[F](emptyInput)(classLoader, "test/r3.txt", ResourcesOptions.default.withUseGzippedIfAvailable))
          .use { port =>
            emptyRequest
              .get(uri"http://localhost:$port")
              .response(asStringAlways)
              .send(backend)
              .map(_.body shouldBe "Resource 3")
          }
          .unsafeToFuture()
      },
      Test("should not return a resource outside of the resource prefix directory") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "test"))
          .use { port => get(port, List("..", "test", "r5.txy")).map(_.body should not be "Resource 5") }
          .unsafeToFuture()
      },
      Test("should not return a resource outside of the resource prefix directory, when the path is given as a single segment") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "test"))
          .use { port => get(port, List("../test2/r5.txt")).map(_.body should not be "Resource 5") }
          .unsafeToFuture()
      },
      Test("should serve resources") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "test"))
          .use { port =>
            get(port, "r1.txt" :: Nil).map(_.body shouldBe "Resource 1") >>
              get(port, "r2.txt" :: Nil).map(_.body shouldBe "Resource 2") >>
              get(port, "d1" :: "r3.txt" :: Nil).map(_.body shouldBe "Resource 3") >>
              get(port, "d1" :: "d2" :: "r4.txt" :: Nil).map(_.body shouldBe "Resource 4")
          }
          .unsafeToFuture()
      },
      Test("should serve resources with filter") {
        serveRoute(
          resourcesGetServerEndpoint[F](emptyInput)(
            classLoader,
            "test",
            ResourcesOptions.default.resourceFilter(_.exists(_.contains("2")))
          )
        )
          .use { port =>
            get(port, "r1.txt" :: Nil).map(_.code shouldBe StatusCode.NotFound) >>
              get(port, "r2.txt" :: Nil).map(_.body shouldBe "Resource 2") >>
              get(port, "d1" :: "r3.txt" :: Nil).map(_.code shouldBe StatusCode.NotFound) >>
              get(port, "d1" :: "d2" :: "r4.txt" :: Nil).map(_.body shouldBe "Resource 4")
          }
          .unsafeToFuture()
      },
      Test("should return 404 when a resource is not found") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "test"))
          .use { port => get(port, List("r3")).map(_.code shouldBe StatusCode.NotFound) }
          .unsafeToFuture()
      },
      Test("should return default resource when resource is not found") {
        serveRoute(
          resourcesGetServerEndpoint[F]("test")(classLoader, "test", ResourcesOptions.default.defaultResource(List("d1", "r3.txt")))
        )
          .use { port =>
            get(port, List("test", "r10.txt")).map(_.body shouldBe "Resource 3") >>
              get(port, List("test", "r1.txt")).map(_.body shouldBe "Resource 1")
          }
          .unsafeToFuture()
      },
      Test("should serve a single file from the given system path") {
        withTestFilesDirectory { testDir =>
          serveRoute(fileGetServerEndpoint[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
            .use { port => get(port, List("test")).map(_.body shouldBe "f1 content") }
            .unsafeToFuture()
        }
      },
      Test("if an etag is present, should only return the resource if it doesn't match the etag") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "test"))
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
      },
      Test("should serve index.html when a resource directory is requested (from file)") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "test"))
          .use { port =>
            get(port, List("d1")).map(_.body shouldBe "Index resource")
          }
          .unsafeToFuture()
      },
      Test("should serve a resource from a jar") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "META-INF/maven/org.slf4j/slf4j-api"))
          .use { port =>
            get(port, List("pom.properties")).map(_.body should include("groupId=org.slf4j"))
          }
          .unsafeToFuture()
      },
      Test("should return 404 when a resource directory is requested from jar and index.html does not exist") {
        serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classLoader, "META-INF/maven/org.slf4j/slf4j-api"))
          .use { port =>
            get(port, Nil).map(_.code shouldBe StatusCode.NotFound)
          }
          .unsafeToFuture()
      }
    )
    val resourceMetadataTest = Test("should return resource metadata") {
      serveRoute(resourcesGetServerEndpoint[F](emptyInput)(classOf[ServerStaticContentTests[F, OPTIONS, ROUTE]].getClassLoader, "test"))
        .use { port =>
          get(port, List("r1.txt")).map { r =>
            val headersToValues = r.headers.map(h => (h.name.toLowerCase, h.value))
            headersToValues should (contain((HeaderNames.ContentLength.toLowerCase, Some("10"))) or contain(
              (HeaderNames.TransferEncoding.toLowerCase, "chunked")
            ))
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

  def serveRoute(e: ServerEndpoint[Any, F]): Resource[IO, Port] =
    serverInterpreter.server(
      NonEmptyList.of(
        serverInterpreter.route(e, (ci: CustomiseInterceptors[F, OPTIONS]) => ci.decodeFailureHandler(DefaultDecodeFailureHandler.default))
      )
    )

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
