package sttp.tapir.server.vertx.zio

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import _root_.zio.RIO
import _root_.zio.blocking.Blocking
import sttp.tapir.ztapir.RIOMonadError
import zio.stream.ZStream

class ZioVertxServerTest extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[RIO[Blocking, *]] = new RIOMonadError[Blocking]
      val interpreter = new ZioVertxTestServerInterpreter(vertx)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false, options = false).tests() ++
        new ServerMultipartTests(
          createServerTest,
          partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
          partOtherHeaderSupport = false
        ).tests() ++
        new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
        new ServerWebSocketTests(createServerTest, ZioStreams) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
          override def emptyPipe[A, B]: streams.Pipe[A, B] = _ => ZStream.empty
        }.tests()
    }
  }
}
