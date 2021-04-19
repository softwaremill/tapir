package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerStreamingTests, CreateServerTest, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class CatsVertxServerTest extends TestSuite {
  import VertxCatsServerInterpreter._

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => new CatsFFromVFuture[IO]().apply(vertx.close).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[IO] = VertxCatsServerInterpreter.monadError[IO]
      val interpreter = new CatsVertxTestServerInterpreter(vertx)
      val createServerTest = new CreateServerTest(interpreter)

      new ServerBasicTests(
        backend,
        createServerTest,
        interpreter,
        multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
      ).tests() ++
        new ServerAuthenticationTests(backend, createServerTest).tests() ++
        new ServerStreamingTests(backend, createServerTest, Fs2Streams.apply[IO]).tests()
    }
  }
}
