package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.monad.FutureMonad
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.ExecutionContext

class VertxServerTests extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: FutureMonad = new FutureMonad()(ExecutionContext.global)
      val interpreter = new VertxTestServerInterpreter(vertx)
      val serverTests = new ServerTests(interpreter)

      new ServerBasicTests(
        backend,
        serverTests,
        interpreter,
        multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
      ).tests() ++ new ServerAuthenticationTests(backend, serverTests).tests()
    }
  }
}
