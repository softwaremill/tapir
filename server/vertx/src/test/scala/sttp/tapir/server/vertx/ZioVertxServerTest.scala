package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests.{
  DefaultCreateServerTest,
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerFileMultipartTests,
  ServerStreamingTests,
  backendResource
}
import sttp.tapir.server.vertx.VertxZioServerInterpreter.RioFromVFuture
import sttp.tapir.tests.{Test, TestSuite}
import zio.Task
import zio.interop.catz._

class ZioVertxServerTest extends TestSuite {
  import ZioVertxTestServerInterpreter._

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[Task] = VertxZioServerInterpreter.monadError
      val interpreter = new ZioVertxTestServerInterpreter(vertx)
      val createServerTest =
        new DefaultCreateServerTest(backend, interpreter)
          .asInstanceOf[DefaultCreateServerTest[Task, ZioStreams, Router => Route, RoutingContext => Unit]]

      new ServerBasicTests(createServerTest, interpreter).tests() ++
        new ServerFileMultipartTests(
          createServerTest,
          multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
        ).tests() ++
        new ServerAuthenticationTests(createServerTest).tests() ++
        new ServerStreamingTests(createServerTest, ZioStreams).tests()
    }
  }
}
