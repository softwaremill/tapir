package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.tests._

import java.io.File
import java.nio.file.{Files, StandardOpenOption}
import java.util.Comparator
import scala.concurrent.Future

class ServerStaticContentTests[F[_], ROUTE](
    serverInterpreter: TestServerInterpreter[F, Any, ROUTE],
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets]
) {

  def tests(): List[Test] = List(
    Test("serve files from the given system path") {
      withTestFilesDirectory { testDir =>
        serverInterpreter
          .server(NonEmptyList.of(serverInterpreter.route(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))))
          .use { port =>
            def get(path: List[String]) = basicRequest
              .get(uri"http://localhost:$port/$path")
              .response(asStringAlways)
              .send(backend)

            get("f1" :: Nil).map(_.body shouldBe "f1 content") >>
              get("f2" :: Nil).map(_.body shouldBe "f2 content") >>
              get("d1/f3" :: Nil).map(_.body shouldBe "f3 content") >>
              get("d1/d2/f4" :: Nil).map(_.body shouldBe "f4 content")
          }
          .unsafeToFuture()
      }
    },
    Test("return 404 when files are not found") {
      withTestFilesDirectory { testDir =>
        serverInterpreter
          .server(NonEmptyList.of(serverInterpreter.route(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))))
          .use { port =>
            basicRequest
              .get(uri"http://localhost:$port/f3")
              .response(asStringAlways)
              .send(backend)
              .map(_.code shouldBe StatusCode.NotFound)
          }
          .unsafeToFuture()
      }
    },
    Test("return file metadata") {
      withTestFilesDirectory { testDir =>
        serverInterpreter
          .server(NonEmptyList.of(serverInterpreter.route(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))))
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
    Test("if an etag is present, only return the file if it doesn't match the etag") {
      withTestFilesDirectory { testDir =>
        serverInterpreter
          .server(NonEmptyList.of(serverInterpreter.route(filesServerEndpoint[F](emptyInput)(testDir.getAbsolutePath))))
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
    }
  )

  def withTestFilesDirectory[T](t: File => Future[T]): Future[T] = {
    val parent = Files.createTempDirectory("tapir-tests")

    parent.resolve("d1/d2").toFile.mkdirs()

    Files.write(parent.resolve("f1"), "f1 content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("f2"), "f2 content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("img.gif"), "img content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("d1/f3"), "f3 content".getBytes, StandardOpenOption.CREATE_NEW)
    Files.write(parent.resolve("d1/d2/f4"), "f4 content".getBytes, StandardOpenOption.CREATE_NEW)
    parent.toFile.deleteOnExit()

    import scala.concurrent.ExecutionContext.Implicits.global
    val result = t(parent.toFile)
    result.onComplete(_ =>
      Files
        .walk(parent)
        .sorted(Comparator.reverseOrder())
        .map(f => f.toFile)
        .forEach(f => { val _ = f.delete })
    )

    result
  }
}
