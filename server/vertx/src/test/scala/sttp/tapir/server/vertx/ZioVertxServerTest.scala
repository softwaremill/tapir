package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import zio.Task

class ZioVertxServerTest extends TestSuite {

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[Task] = VertxZioServerInterpreter.monadError
      val interpreter = new ZioVertxTestServerInterpreter(vertx)
      val createServerTest = new CreateServerTest(interpreter)

      new ServerBasicTests(
        backend,
        createServerTest,
        interpreter,
        multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
      ).tests() ++
        new ServerAuthenticationTests(backend, createServerTest).tests() ++
        new ServerStreamingTests(backend, createServerTest, ZioStreams).tests()
    }
  }
}
