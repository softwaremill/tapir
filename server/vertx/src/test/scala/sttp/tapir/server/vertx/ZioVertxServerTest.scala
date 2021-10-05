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
  ServerStaticContentTests,
  ServerStreamingTests,
  backendResource
}
import sttp.tapir.tests.{Test, TestSuite}
import zio.Task

class ZioVertxServerTest extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[Task] = VertxZioServerInterpreter.monadError
      val interpreter = new ZioVertxTestServerInterpreter(vertx)
      val createServerTest =
        new DefaultCreateServerTest(backend, interpreter)
          .asInstanceOf[DefaultCreateServerTest[Task, ZioStreams, Router => Route]]

      new ServerBasicTests(createServerTest, interpreter, invulnerableToUnsanitizedHeaders = false).tests() ++
        new ServerFileMultipartTests(
          createServerTest,
          multipartInlineHeaderSupport = false // README: doesn't seem supported but I may be wrong
        ).tests() ++
        new ServerAuthenticationTests(createServerTest).tests() ++
        new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
        new ServerStaticContentTests(interpreter, backend).tests()
    }
  }
}
