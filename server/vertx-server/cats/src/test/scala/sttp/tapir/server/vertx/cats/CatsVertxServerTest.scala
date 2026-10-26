package sttp.tapir.server.vertx.cats

import cats.effect.{IO, Resource}
import fs2.Stream
import io.vertx.core.Vertx
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.CatsFFromVFuture
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.duration._

class CatsVertxServerTest extends TestSuite {

  // The Vert.x WebSocket tests hit a rare upstream flake: a client frame sent in the brief window after the 101
  // handshake but before Vert.x completes toWebSocket() (so before we can register a frame handler) is silently
  // dropped, so the echo never returns and the test times out. Not fixable via Vert.x's public per-request API
  // (toWebSocket() returns an already-flowing socket); to be reported upstream. Retry failed tests to avoid flaky CI.
  override def retries = 3

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx =>
      // vertx.close sometimes never completes; bound the wait so suite cleanup can't hang the (non-forked) test JVM
      new CatsFFromVFuture[IO]()
        .apply(vertx.close)
        .void
        .timeout(30.seconds)
        .handleErrorWith(e => IO(println(s"Vertx close failed or timed out: $e")))
    )

  def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
    stream.compile.drain.void

  override def tests: Resource[IO, List[Test]] = backendResource
    // for streaming requests, vertx responds with transfer-encoding header, which is not supported by http2
    // however, connections are negotiated with http2; hence, forcing http1 for these tests to work
    .map(backend => new ForceHttp1BackendWrapper(backend))
    .flatMap { backend =>
      vertxResource.map { implicit vertx =>
        implicit val m: MonadError[IO] = VertxCatsServerInterpreter.monadError[IO]
        val interpreter = new CatsVertxTestServerInterpreter(vertx, dispatcher)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        new AllServerTests(
          createServerTest,
          interpreter,
          backend,
          multipart = false,
          reject = false,
          options = false,
          metrics = false
        ).tests() ++
          new ServerMultipartTests(
            createServerTest,
            partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
            partOtherHeaderSupport = false
          ).tests() ++
          new ServerStreamingTests(createServerTest).tests(Fs2Streams.apply[IO])(drainFs2) ++
          new ServerWebSocketTests(
            createServerTest,
            Fs2Streams.apply[IO],
            autoPing = false,
            handlePong = true,
            expectCloseResponse = false,
            frameConcatenation = false,
            // Vert.x sometimes ends the read stream (endHandler) without surfacing the client's CLOSE frame via the
            // frameHandler, so the close cannot be reliably decoded as a `None` to the request pipe (same limitation
            // as http4s). Disable the close-frame-as-None test to avoid a flaky failure.
            decodeCloseRequests = false
          ) {
            override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
            override def emptyPipe[A, B]: streams.Pipe[A, B] = _ => Stream.empty
          }.tests() ++ new ServerMetricsTest(createServerTest, interpreter, supportsMetricsDecodeFailureCallbacks = false).tests()
      }
    }
}
