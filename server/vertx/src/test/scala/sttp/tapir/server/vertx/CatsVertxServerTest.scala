package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{
  DefaultCreateServerTest,
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerFileMultipartTests,
  ServerStreamingTests,
  backendResource
}
import sttp.tapir.server.vertx.VertxCatsServerInterpreter.CatsFFromVFuture
import sttp.tapir.tests.{Test, TestSuite}

class CatsVertxServerTest extends TestSuite {

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => new CatsFFromVFuture[IO]().apply(vertx.close).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[IO] = VertxCatsServerInterpreter.monadError[IO]
      val interpreter = new CatsVertxTestServerInterpreter(vertx, dispatcher)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      new ServerBasicTests(createServerTest, interpreter).tests() ++
        new ServerFileMultipartTests(
          createServerTest,
          multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
        ).tests() ++
        new ServerAuthenticationTests(createServerTest).tests() ++
        new ServerStreamingTests(createServerTest, Fs2Streams.apply[IO]).tests()
    }
  }
}
