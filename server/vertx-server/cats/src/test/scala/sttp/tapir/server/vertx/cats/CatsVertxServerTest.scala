package sttp.tapir.server.vertx.cats

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.CatsFFromVFuture
import sttp.tapir.tests.{Test, TestSuite}

class CatsVertxServerTest extends TestSuite {

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => new CatsFFromVFuture[IO]().apply(vertx.close).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[IO] = VertxCatsServerInterpreter.monadError[IO]
      val interpreter = new CatsVertxTestServerInterpreter(vertx, dispatcher)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false).tests() ++
        new ServerMultipartTests(
          createServerTest,
          partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
          partOtherHeaderSupport = false
        ).tests() ++
        new ServerStreamingTests(createServerTest, Fs2Streams.apply[IO]).tests()
    }
  }
}
