package sttp.tapir.server.vertx

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import org.scalatest.BeforeAndAfterAll
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.zio.ZioStreams
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest
import zio.{Runtime, Task}

import scala.concurrent.Future

class VertxFutureStubServerTest extends ServerStubInterpreterTest[Future, Any, VertxFutureServerOptions] {
  override def customInterceptors: CustomInterceptors[Future, VertxFutureServerOptions] = VertxFutureServerOptions.customInterceptors
  override def monad: MonadError[Future] = new FutureMonad()
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class VertxCatsStubServerTest extends ServerStubInterpreterTest[IO, Fs2Streams[IO], VertxCatsServerOptions[IO]] with BeforeAndAfterAll {
  private val (dispatcher, shutdownDispatcher) = Dispatcher[IO].allocated.unsafeRunSync()

  override def customInterceptors: CustomInterceptors[IO, VertxCatsServerOptions[IO]] =
    VertxCatsServerOptions.customInterceptors(dispatcher)
  override def monad: MonadError[IO] = new CatsMonadError[IO]
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()

  override protected def afterAll(): Unit = shutdownDispatcher.unsafeRunSync()
}

class VertxZioStubServerTest extends ServerStubInterpreterTest[Task, ZioStreams, VertxZioServerOptions[Task]] {
  override def customInterceptors: CustomInterceptors[Task, VertxZioServerOptions[Task]] = VertxZioServerOptions.customInterceptors
  override def monad: MonadError[Task] = VertxZioServerInterpreter.monadError
  override def asFuture[A]: Task[A] => Future[A] = task => Runtime.default.unsafeRunToFuture(task)
}
