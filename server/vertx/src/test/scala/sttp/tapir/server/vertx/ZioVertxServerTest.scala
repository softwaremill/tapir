package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{ServerAuthenticationTests, ServerBasicTests, ServerStreamingTests, CreateTestServer, backendResource}
import sttp.tapir.tests.{Test, TestSuite}
import zio.interop.catz._
import zio.Task

class ZioVertxServerTest extends TestSuite {
  import VertxZioServerInterpreter._
  import ZioVertxTestServerInterpreter._

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(Task.effect(Vertx.vertx()))(vertx => new RioFromVFuture[Any].apply(vertx.close).unit).mapK(zioToIo)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[Task] = VertxZioServerInterpreter.monadError
      val interpreter = new ZioVertxTestServerInterpreter(vertx)
      val createServerTest = new CreateTestServer(interpreter)

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
