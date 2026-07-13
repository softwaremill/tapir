package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.{Handler, Vertx}
import sttp.monad.FutureMonad
import io.vertx.core.streams.ReadStream
import sttp.tapir.server.tests._
import sttp.tapir.server.vertx.streams.VertxStreams
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import io.vertx.core.buffer.Buffer

class VertxServerTest extends TestSuite {

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
        implicit val m: FutureMonad = new FutureMonad()(ExecutionContext.global)

        val interpreter = new VertxTestServerInterpreter(vertx)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        def drainVertx[T](source: ReadStream[T]): Future[Unit] = {
          val p = Promise[Unit]()
          // Handler for stream data - do nothing with the data
          val dataHandler: Handler[T] = (_: T) => ()

          // End handler - complete the promise when the stream ends
          val endHandler: Handler[Void] = (_: Void) => p.success(())

          // Exception handler - fail the promise if an error occurs
          val exceptionHandler: Handler[Throwable] = (t: Throwable) => p.failure(t)

          source.handler(dataHandler).endHandler(endHandler).exceptionHandler(exceptionHandler).fetch(Long.MaxValue).resume()

          p.future
        }

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
            partContentTypeHeaderSupport = true,
            partOtherHeaderSupport = false
          ).tests() ++ new ServerStreamingTests(createServerTest).tests(VertxStreams)(drainVertx[Buffer]) ++
          (new ServerWebSocketTests(
            createServerTest,
            VertxStreams,
            autoPing = false,
            handlePong = true,
            expectCloseResponse = false,
            frameConcatenation = false
          ) {
            override def functionToPipe[A, B](f: A => B): VertxStreams.Pipe[A, B] = in => new ReadStreamMapping(in, f)
            override def emptyPipe[A, B]: VertxStreams.Pipe[A, B] = _ => new EmptyReadStream()
          }).tests() ++ new ServerMetricsTest(createServerTest, interpreter, supportsMetricsDecodeFailureCallbacks = false).tests()
      }
    }
}

class EmptyReadStream[B]() extends ReadStream[B] {
  private var endHandler: Handler[Void] = _
  def endHandler(handler: Handler[Void]): ReadStream[B] = {
    endHandler = handler
    this
  }
  def exceptionHandler(handler: Handler[Throwable]): ReadStream[B] = {
    this
  }
  def fetch(x: Long): ReadStream[B] = {
    endHandler.handle(null)
    this
  }
  def handler(handler: io.vertx.core.Handler[B]): ReadStream[B] = {
    this
  }
  def pause(): ReadStream[B] = {
    this
  }
  def resume(): ReadStream[B] = {
    this
  }
}
