package sttp.tapir.server.vertx.zio

import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.tapir._
import _root_.zio.{Task, ZIO}
import _root_.zio.stream.ZStream
import org.scalatest.OptionValues
import sttp.client3.basicRequest
import sttp.tapir.ztapir.RIOMonadError
import org.scalatest.matchers.should.Matchers._

class ZioVertxServerTest extends TestSuite with OptionValues {
  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[Task] = new RIOMonadError[Any]
      val interpreter = new ZioVertxTestServerInterpreter(vertx)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      def additionalTests(): List[Test] = List(
        createServerTest.testServer(
          endpoint.out(plainBody[String]),
          "Do not execute effects on vert.x thread"
        )((_: Unit) => ZIO.attempt(Thread.currentThread().getName).asRight) { (backend, baseUri) =>
          basicRequest.get(baseUri).send(backend).map(_.body.toOption.value should not include "vert.x-eventloop-thread")
        }
      )

      new AllServerTests(createServerTest, interpreter, backend, multipart = false, reject = false, options = false).tests() ++
        new ServerMultipartTests(
          createServerTest,
          partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
          partOtherHeaderSupport = false
        ).tests() ++ additionalTests() ++
        new ServerStreamingTests(createServerTest, ZioStreams).tests() ++
        new ServerWebSocketTests(createServerTest, ZioStreams) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
          override def emptyPipe[A, B]: streams.Pipe[A, B] = _ => ZStream.empty
        }.tests()
    }
  }
}
