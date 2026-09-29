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

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.{CompletableFuture, Executors, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// Reproduces https://github.com/softwaremill/tapir/issues/5458: with a compute pool that can't compensate for blocked
// threads (unlike cats-effect's work-stealing pool, e.g. a ZIO executor), as many concurrent streaming responses as
// there are compute threads must not freeze the server.
class CatsVertxLimitedThreadPoolStreamingTest extends AnyFunSuite with Matchers {
  private val Threads = 2

  test("streaming responses don't deadlock when the effect runs on a small fixed thread pool") {
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

    val streamEndpoint = endpoint.get
      .in("stream")
      .out(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), None))
      .serverLogicSuccess[IO](_ => IO.sleep(200.millis).as(Stream.emits("hello, world!".getBytes.toIndexedSeq)))

    // the dispatcher isn't released: on deadlock, the compute pool it would run on is stuck
    val port = Dispatcher
      .parallel[IO]
      .allocated
      .flatMap { case (dispatcher, _) =>
        IO.fromCompletableFuture(IO.delay {
          val router = Router.router(vertx)
          val _ = VertxCatsServerInterpreter[IO](dispatcher).route(streamEndpoint)(router)
          vertx.createHttpServer(new HttpServerOptions()).requestHandler(router).listen(0).toCompletionStage.toCompletableFuture
        }).map(_.actualPort())
      }
      .unsafeRunSync()(runtime)

    try {
      val client = HttpClient.newHttpClient()
      val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/stream")).GET().build()
      val responses = (1 to Threads).map(_ => client.sendAsync(request, HttpResponse.BodyHandlers.ofString()))

      CompletableFuture.allOf(responses: _*).get(20, TimeUnit.SECONDS)

      responses.map(_.get().body()) shouldBe List.fill(Threads)("hello, world!")
    } finally {
      computePool.shutdownNow()
      blockingPool.shutdownNow()
      shutdownScheduler()
      val _ = vertx.close()
    }
  }
}
