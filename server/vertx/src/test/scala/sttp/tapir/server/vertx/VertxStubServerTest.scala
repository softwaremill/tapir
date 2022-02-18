package sttp.tapir.server.vertx

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.zio.ZioStreams
import sttp.client3.testing.SttpBackendStub
import sttp.monad.FutureMonad
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubInterpreterStreamingTest, ServerStubInterpreterTest}
import zio.stream.ZStream
import zio.{Runtime, Task}

import scala.concurrent.{ExecutionContext, Future}

object VertxFutureCreateServerStubTest extends CreateServerStubTest[Future, VertxFutureServerOptions] {
  override def customInterceptors: CustomInterceptors[Future, VertxFutureServerOptions] = VertxFutureServerOptions.customInterceptors
  override def stub[R]: SttpBackendStub[Future, R] = SttpBackendStub(new FutureMonad()(ExecutionContext.global))
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class VertxFutureServerStubTest extends ServerStubInterpreterTest(VertxFutureCreateServerStubTest)

class VertxCatsCreateServerStubTest extends CreateServerStubTest[IO, VertxCatsServerOptions[IO]] {
  private val (dispatcher, shutdownDispatcher) = Dispatcher[IO].allocated.unsafeRunSync()

  override def customInterceptors: CustomInterceptors[IO, VertxCatsServerOptions[IO]] =
    VertxCatsServerOptions.customInterceptors(dispatcher)
  override def stub[R]: SttpBackendStub[IO, R] = SttpBackendStub(new CatsMonadError[IO])
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()

  override def cleanUp(): Unit = shutdownDispatcher.unsafeRunSync()
}

class VertxCatsServerStubTest extends ServerStubInterpreterTest(new VertxCatsCreateServerStubTest)

class VertxCatsServerStubStreamingTest extends ServerStubInterpreterStreamingTest(new VertxCatsCreateServerStubTest, Fs2Streams[IO]) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = fs2.Stream.apply[IO, String]("hello")
}

object VertxZioCreateServerStubTest extends CreateServerStubTest[Task, VertxZioServerOptions[Task]] {
  override def customInterceptors: CustomInterceptors[Task, VertxZioServerOptions[Task]] = VertxZioServerOptions.customInterceptors
  override def stub[R]: SttpBackendStub[Task, R] = SttpBackendStub(VertxZioServerInterpreter.monadError)
  override def asFuture[A]: Task[A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}

class VertxZioServerStubTest extends ServerStubInterpreterTest(VertxZioCreateServerStubTest)

class VertxZioServerStubStreamingTest extends ServerStubInterpreterStreamingTest(VertxZioCreateServerStubTest, ZioStreams) {

  /** Must be an instance of streams.BinaryStream */
  override def sampleStream: Any = ZStream.fromIterable(List("hello"))
}
