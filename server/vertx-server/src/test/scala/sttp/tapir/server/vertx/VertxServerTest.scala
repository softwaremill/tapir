package sttp.tapir.server.vertx

import cats.effect.{IO, Resource}
import io.vertx.core.{Handler, Vertx}
import sttp.monad.FutureMonad
import io.vertx.core.streams.ReadStream
import sttp.tapir.server.tests._
import sttp.tapir.server.vertx.streams.VertxStreams
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class VertxServerTest extends TestSuite {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: FutureMonad = new FutureMonad()(ExecutionContext.global)

      val interpreter = new VertxTestServerInterpreter(vertx)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false, options = false).tests() ++
        new ServerMultipartTests(
          createServerTest,
          partContentTypeHeaderSupport = true,
          partOtherHeaderSupport = false
        ).tests() ++ new ServerStreamingTests(createServerTest, maxLengthSupported = false).tests(VertxStreams)(_ => Future.unit) ++
        (new ServerWebSocketTests(createServerTest, VertxStreams) {
          override def functionToPipe[A, B](f: A => B): VertxStreams.Pipe[A, B] = in => new ReadStreamMapping(in, f)
          override def emptyPipe[A, B]: VertxStreams.Pipe[A, B] = _ => new EmptyReadStream()
        }).tests()
    }
  }
}

class EmptyReadStream[B]() extends ReadStream[B] {
  private var endHandler: Handler[Void] = _
  def endHandler(handler: Handler[Void]): ReadStream[B] = {
    endHandler = handler
    this
  }
  def exceptionHandler(handler: Handler[Throwable]): ReadStream[B] = {
    this
  }
  def fetch(x: Long): ReadStream[B] = {
    endHandler.handle(null)
    this
  }
  def handler(handler: io.vertx.core.Handler[B]): ReadStream[B] = {
    this
  }
  def pause(): ReadStream[B] = {
    this
  }
  def resume(): ReadStream[B] = {
    this
  }
}
