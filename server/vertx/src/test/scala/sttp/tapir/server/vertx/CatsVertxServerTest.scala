package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{CreateTestServer, ServerAuthenticationTests, ServerBasicTests, ServerFileMutltipartTests, ServerStreamingTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class CatsVertxServerTest extends TestSuite {
  import VertxCatsServerInterpreter._

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => new CatsFFromVFuture[IO]().apply(vertx.close).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[IO] = VertxCatsServerInterpreter.monadError[IO]
      val interpreter = new CatsVertxTestServerInterpreter(vertx)
      val createTestServer = new CreateTestServer(backend, interpreter)

      new ServerBasicTests(createTestServer, interpreter).tests() ++
        new ServerFileMutltipartTests(
          createTestServer,
          multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
        ).tests()
        new ServerAuthenticationTests(createTestServer).tests() ++
        new ServerStreamingTests(createTestServer, Fs2Streams.apply[IO]).tests()
    }
  }
}
