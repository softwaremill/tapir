package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.ext.web.{Route, Router}
import sttp.monad.FutureMonad
import sttp.tapir.server.tests._
import sttp.tapir.server.vertx.streams.ReactiveStreams
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.{ExecutionContext, Future}

class VertxServerTest extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: FutureMonad = new FutureMonad()(ExecutionContext.global)

      val interpreter = new VertxTestServerInterpreter(vertx)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)
          .asInstanceOf[DefaultCreateServerTest[Future, ReactiveStreams, VertxFutureServerOptions, Router => Route]]

      new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false, options = false).tests() ++
        new ServerMultipartTests(
          createServerTest,
          partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
          partOtherHeaderSupport = false
        ).tests() ++ new ServerStreamingTests(createServerTest, ReactiveStreams).tests()
    }
  }
}
