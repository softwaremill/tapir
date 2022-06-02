package sttp.tapir.server.vertx.zio

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import _root_.zio.Task

class ZioVertxServerTest extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[Task] = VertxZioServerInterpreter.monadError
      val interpreter = new ZioVertxTestServerInterpreter(vertx)
      val createServerTest =
        new DefaultCreateServerTest(backend, interpreter)
          .asInstanceOf[DefaultCreateServerTest[Task, ZioStreams, VertxZioServerOptions[Task], Router => Route]]

      new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false).tests() ++
        new ServerMultipartTests(
          createServerTest,
          partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
          partOtherHeaderSupport = false
        ).tests() ++
        new ServerStreamingTests(createServerTest, ZioStreams).tests()
    }
  }
}
