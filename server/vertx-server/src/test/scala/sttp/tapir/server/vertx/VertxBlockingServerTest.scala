package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.monad.FutureMonad
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.ExecutionContext

class VertxBlockingServerTest extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource
    // for streaming requests, vertx responds with transfer-encoding header, which is not supported by http2
    // however, connections are negotiated with http2; hence, forcing http1 for these tests to work
    .map(backend => new ForceHttp1BackendWrapper(backend))
    .flatMap { backend =>
      vertxResource.map { implicit vertx =>
        implicit val m: FutureMonad = new FutureMonad()(ExecutionContext.global)
        val interpreter = new VertxTestServerBlockingInterpreter(vertx)
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false, options = false, metrics = false)
          .tests() ++
          new ServerMultipartTests(
            createServerTest,
            partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
            partOtherHeaderSupport = false
          ).tests() ++
          new ServerMetricsTest(createServerTest, interpreter, supportsMetricsDecodeFailureCallbacks = false).tests()
      }
    }
}
