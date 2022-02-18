package sttp.tapir.server.netty

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import sttp.client3.testing.SttpBackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubInterpreterTest}

import scala.concurrent.{ExecutionContext, Future}

object NettyFutureCreateServerStubTest extends CreateServerStubTest[Future, NettyFutureServerOptions] {
  override def customInterceptors: CustomInterceptors[Future, NettyFutureServerOptions] = NettyFutureServerOptions.customInterceptors
  override def stub[R]: SttpBackendStub[Future, R] = SttpBackendStub(new FutureMonad()(ExecutionContext.global))
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class NettyFutureServerStubTest extends ServerStubInterpreterTest(NettyFutureCreateServerStubTest)

class NettyCatsCreateServerStubTest extends CreateServerStubTest[IO, NettyCatsServerOptions[IO]] {
  val (dispatcher, shutdownDispatcher) = Dispatcher[IO].allocated.unsafeRunSync()

  override def customInterceptors: CustomInterceptors[IO, NettyCatsServerOptions[IO]] =
    NettyCatsServerOptions.customInterceptors[IO](dispatcher)
  override def stub[R]: SttpBackendStub[IO, R] = SttpBackendStub(new CatsMonadError[IO]())
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()

  override def cleanUp(): Unit = shutdownDispatcher.unsafeToFuture()
}

class NettyCatsServerStubTest extends ServerStubInterpreterTest(new NettyCatsCreateServerStubTest)
