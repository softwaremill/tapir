package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.monad.FutureMonad
import sttp.tapir.server.tests.{CreateTestServer, ServerAuthenticationTests, ServerBasicTests, ServerFileMutltipartTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.ExecutionContext

class VertxBlockingServerTest extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: FutureMonad = new FutureMonad()(ExecutionContext.global)
      val interpreter = new VertxTestServerBlockingInterpreter(vertx)
      val createTestServer = new CreateTestServer(backend, interpreter)

      new ServerBasicTests(createTestServer, interpreter).tests() ++
        new ServerFileMutltipartTests(
          createTestServer,
          multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
        ).tests() ++
        new ServerAuthenticationTests(createTestServer).tests()
    }
  }
}
