package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
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
    backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets]
) {

  def tests(): List[Test] = List(
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
    Test("return 404 when files are not found") {
      withTestFilesDirectory { testDir =>
        serveRoute(filesServerEndpointRanged[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
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
    Test("Returns 416 if range header not present") {
      withTestFilesDirectory { testDir =>
        serveRoute(filesServerEndpointRanged[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
          .use { port =>
            basicRequest
              .get(uri"http://localhost:$port/test")
              .response(asStringAlways)
              .send(backend)
              .map(_.code.code shouldBe 416)
          }
          .unsafeToFuture()
      }
    },
    Test("Returns content range header with matching bytes") {
      withTestFilesDirectory { testDir =>
        serveRoute(filesServerEndpointRanged[F]("test")(testDir.toPath.resolve("f1").toFile.getAbsolutePath))
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
    }
  )

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
