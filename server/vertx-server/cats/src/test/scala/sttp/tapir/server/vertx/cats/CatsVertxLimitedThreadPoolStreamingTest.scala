package sttp.tapir.server.vertx.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import fs2.Stream
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.{CompletableFuture, Executors, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// Reproduces https://github.com/softwaremill/tapir/issues/5458: with a compute pool that can't compensate for blocked
// threads (unlike cats-effect's work-stealing pool, e.g. a ZIO executor), more concurrent streaming requests or responses than
// there are compute threads must not freeze the server.
class CatsVertxLimitedThreadPoolStreamingTest extends AnyFunSuite with Matchers {
  private val Threads = 2
  private val Requests = Threads * 4

  test("streaming responses don't deadlock when the effect runs on a small fixed thread pool") {
    val streamEndpoint = endpoint.get
      .in("stream")
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), None))
      .serverLogicSuccess[IO](_ => IO.sleep(200.millis).as(Stream.emits("hello, world!".getBytes.toIndexedSeq)))

    val bodies = withServer(streamEndpoint)(port => sendConcurrently(request(port, "stream").GET()))

    bodies shouldBe List.fill(Requests)("hello, world!")
  }

  test("streaming requests don't deadlock when the effect runs on a small fixed thread pool") {
    // the body is decoded only after the security logic, so the sleep makes the requests enter the stream bridge together
    val echoEndpoint = endpoint.post
      .in("echo")
      .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), None))
      .out(stringBody)
      .serverSecurityLogicSuccess[Unit, IO](_ => IO.sleep(200.millis))
      .serverLogicSuccess(_ => body => body.through(fs2.text.utf8.decode).compile.string)

    val bodies =
      withServer(echoEndpoint)(port => sendConcurrently(request(port, "echo").POST(HttpRequest.BodyPublishers.ofString("hello, world!"))))

    bodies shouldBe List.fill(Requests)("hello, world!")
  }

  private def request(port: Int, path: String): HttpRequest.Builder =
    HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/$path"))

  private def sendConcurrently(request: HttpRequest.Builder): List[String] = {
    val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    val responses = (1 to Requests).map(_ => client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()))

    CompletableFuture.allOf(responses*).get(20, TimeUnit.SECONDS)

    responses.map(_.get().body()).toList
  }

  private def withServer[T](e: ServerEndpoint[Fs2Streams[IO], IO])(f: Int => T): T = {
    val computePool = Executors.newFixedThreadPool(Threads)
    val blockingPool = Executors.newCachedThreadPool()
    val (scheduler, shutdownScheduler) = Scheduler.createDefaultScheduler()
    val runtime = IORuntime(
      ExecutionContext.fromExecutor(computePool),
      ExecutionContext.fromExecutor(blockingPool),
      scheduler,
      () => (),
      IORuntimeConfig()
    )
    val vertx = Vertx.vertx()

    // the dispatcher isn't released: on deadlock, the compute pool it would run on is stuck
    val port = Dispatcher
      .parallel[IO]
      .allocated
      .flatMap { case (dispatcher, _) =>
        IO.fromCompletableFuture(IO.delay {
          val router = Router.router(vertx)
          val _ = VertxCatsServerInterpreter[IO](dispatcher).route(e)(router)
          vertx.createHttpServer(new HttpServerOptions()).requestHandler(router).listen(0).toCompletionStage.toCompletableFuture
        }).map(_.actualPort())
      }
      .unsafeRunSync()(using runtime)

    try f(port)
    finally {
      computePool.shutdownNow()
      blockingPool.shutdownNow()
      shutdownScheduler()
      val _ = vertx.close()
    }
  }
}
