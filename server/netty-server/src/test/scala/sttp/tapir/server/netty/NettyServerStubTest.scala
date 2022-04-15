package sttp.tapir.server.netty

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import sttp.client3.testing.SttpBackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubTest}

import scala.concurrent.{ExecutionContext, Future}

object NettyFutureCreateServerStubTest extends CreateServerStubTest[Future, NettyFutureServerOptions] {
  override def customiseInterceptors: CustomiseInterceptors[Future, NettyFutureServerOptions] =
    NettyFutureServerOptions.customiseInterceptors
  override def stub[R]: SttpBackendStub[Future, R] = SttpBackendStub(new FutureMonad()(ExecutionContext.global))
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class NettyFutureServerStubTest extends ServerStubTest(NettyFutureCreateServerStubTest)

class NettyCatsCreateServerStubTest extends CreateServerStubTest[IO, NettyCatsServerOptions[IO]] {
  val (dispatcher, shutdownDispatcher) = Dispatcher[IO].allocated.unsafeRunSync()

  override def customiseInterceptors: CustomiseInterceptors[IO, NettyCatsServerOptions[IO]] =
    NettyCatsServerOptions.customiseInterceptors[IO](dispatcher)
  override def stub[R]: SttpBackendStub[IO, R] = SttpBackendStub(new CatsMonadError[IO]())
  override def asFuture[A]: IO[A] => Future[A] = io => io.unsafeToFuture()

  override def cleanUp(): Unit = shutdownDispatcher.unsafeToFuture()
}

class NettyCatsServerStubTest extends ServerStubTest(new NettyCatsCreateServerStubTest)
