package sttp.tapir.server.vertx.cats

import cats.effect.{IO, Resource}
import fs2.Stream
import io.vertx.core.Vertx
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.tests._
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.CatsFFromVFuture
import sttp.tapir.tests.{Test, TestSuite}

class CatsVertxServerTest extends TestSuite {

  def vertxResource: Resource[IO, Vertx] =
    Resource.make(IO.delay(Vertx.vertx()))(vertx => new CatsFFromVFuture[IO]().apply(vertx.close).void)

  def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
    stream.compile.drain.void

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    vertxResource.map { implicit vertx =>
      implicit val m: MonadError[IO] = VertxCatsServerInterpreter.monadError[IO]
      val interpreter = new CatsVertxTestServerInterpreter(vertx, dispatcher)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      new AllServerTests(
        createServerTest,
        interpreter,
        backend,
        multipart = false,
        reject = false,
        options = false
      ).tests() ++
        new ServerMultipartTests(
          createServerTest,
          partContentTypeHeaderSupport = false, // README: doesn't seem supported but I may be wrong
          partOtherHeaderSupport = false
        ).tests() ++
        new ServerStreamingTests(createServerTest).tests(Fs2Streams.apply[IO])(drainFs2) ++
        new ServerWebSocketTests(
          createServerTest,
          Fs2Streams.apply[IO],
          autoPing = false,
          failingPipe = true,
          handlePong = true
        ) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
          override def emptyPipe[A, B]: streams.Pipe[A, B] = _ => Stream.empty
        }.tests()
    }
  }
}
