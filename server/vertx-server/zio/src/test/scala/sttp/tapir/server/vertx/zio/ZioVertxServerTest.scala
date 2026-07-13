package sttp.tapir.server.vertx.zio

import _root_.zio.stream.ZStream
import _root_.zio.{Task, ZIO}
import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.zio.ZioStreams
import sttp.client4.basicRequest
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir.ztapir.RIOMonadError
import zio.stream.ZSink

class ZioVertxServerTest extends TestSuite with OptionValues {

  // The Vert.x WebSocket tests hit a rare upstream flake: a client frame sent in the brief window after the 101
  // handshake but before Vert.x completes toWebSocket() (so before we can register a frame handler) is silently
  // dropped, so the echo never returns and the test times out. Not fixable via Vert.x's public per-request API
  // (toWebSocket() returns an already-flowing socket); to be reported upstream. Retry failed tests to avoid flaky CI.
  override def retries = 3

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource
    // for streaming requests, vertx responds with transfer-encoding header, which is not supported by http2
    // however, connections are negotiated with http2; hence, forcing http1 for these tests to work
    .map(backend => new ForceHttp1BackendWrapper(backend))
    .flatMap { backend =>
      vertxResource.map { implicit vertx =>
        implicit val m: MonadError[Task] = new RIOMonadError[Any]
        val interpreter = new ZioVertxTestServerInterpreter(vertx)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        def additionalTests(): List[Test] = List(
          createServerTest.testServer(
            endpoint.out(plainBody[String]),
            "Do not execute effects on vert.x thread"
          )((_: Unit) => ZIO.attempt(Thread.currentThread().getName).asRight) { (backend, baseUri) =>
            basicRequest.get(baseUri).send(backend).map(_.body.toOption.value should not include "vert.x-eventloop-thread")
          }
        )
        def drainZStream(zStream: ZioStreams.BinaryStream): Task[Unit] =
          zStream.run(ZSink.drain)

        new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false, options = false, metrics = false)
          .tests() ++
          new ServerMultipartTests(
            createServerTest,
            partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
            partOtherHeaderSupport = false
          ).tests() ++ additionalTests() ++
          new ServerStreamingTests(createServerTest).tests(ZioStreams)(drainZStream) ++
          new ServerWebSocketTests(
            createServerTest,
            ZioStreams,
            autoPing = true,
            handlePong = true,
            expectCloseResponse = false,
            frameConcatenation = false
          ) {
            override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
            override def emptyPipe[A, B]: streams.Pipe[A, B] = _ => ZStream.empty
          }.tests() ++ new ServerMetricsTest(createServerTest, interpreter, supportsMetricsDecodeFailureCallbacks = false).tests()
      }
    }
}
