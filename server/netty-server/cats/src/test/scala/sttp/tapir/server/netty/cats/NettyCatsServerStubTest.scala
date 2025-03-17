package sttp.tapir.server.netty.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubTest}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class NettyCatsCreateServerStubTest extends CreateServerStubTest[IO, NettyCatsServerOptions[IO]] {
  val (dispatcher, shutdownDispatcher) = Dispatcher.parallel[IO].allocated.unsafeRunSync()

  override def customiseInterceptors: CustomiseInterceptors[IO, NettyCatsServerOptions[IO]] =
    NettyCatsServerOptions.customiseInterceptors[IO](dispatcher)
  override def stub[R]: SttpBackendStub[IO, R] = SttpBackendStub(new CatsMonadError[IO]())
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()

  override def cleanUp(): Unit = Await.ready(shutdownDispatcher.unsafeToFuture(), Duration.Inf)
}

class NettyCatsServerStubTest extends ServerStubTest(new NettyCatsCreateServerStubTest)
