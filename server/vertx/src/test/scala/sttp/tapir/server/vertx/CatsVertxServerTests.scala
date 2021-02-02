package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerStreamingTests, ServerTests, backendResource}
import sttp.tapir.tests.{Test, TestSuite}

class CatsVertxServerTests extends TestSuite {
  import VertxCatsServerInterpreter._

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => vertx.close.liftF[IO].void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[IO] = VertxCatsServerInterpreter.monadError[IO]
      val interpreter = new CatsVertxTestServerInterpreter(vertx)
      val serverTests = new ServerTests(interpreter)

      new ServerBasicTests(
        backend,
        serverTests,
        interpreter,
        multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
      ).tests() ++
        new ServerAuthenticationTests(backend, serverTests).tests() ++
        new ServerStreamingTests(backend, serverTests, Fs2Streams.apply[IO]).tests()
    }
  }
}
