package sttp.tapir.server.vertx.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import io.vertx.core.Vertx
import io.vertx.ext.web.{Route, Router}
import org.scalatest.funsuite.AnyFunSuite

// Guards that the per-test server Resource can be acquired more than once. The flaky-WebSocket-test retry
// (TestSuite.retries) re-runs the whole test, hence re-uses this Resource; if the server were created eagerly
// (outside the acquire) rather than inside it, the second listen() would hit Vert.x's CleanableHttpServer guard
// and throw IllegalStateException, defeating the retry. See CatsVertxTestServerInterpreter.server.
class ServerResourceRepeatableSpec extends AnyFunSuite {
  test("server Resource can be acquired twice without IllegalStateException") {
    val vertx = Vertx.vertx()
    try {
      Dispatcher
        .parallel[IO]
        .use { dispatcher =>
          val interpreter = new CatsVertxTestServerInterpreter(vertx, dispatcher)
          val trivialRoute: Router => Route = router => router.route()
          val res = interpreter.server(trivialRoute, None)
          // first use = original run; second use = the retry
          res.use(_ => IO.unit) >> res.use(port => IO(assert(port > 0)))
        }
        .unsafeRunSync()
    } finally { val _ = vertx.close() }
  }
}
