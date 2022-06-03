package sttp.tapir.server.netty

import sttp.client3.testing.SttpBackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubTest}

import scala.concurrent.{ExecutionContext, Future}

object NettyFutureCreateServerStubTest extends CreateServerStubTest[Future, NettyFutureServerOptions] {
  override def customiseInterceptors: CustomiseInterceptors[Future, NettyFutureServerOptions] =
    NettyFutureServerOptions.customiseInterceptors(NettyFutureServerOptions.tcp)
  override def stub[R]: SttpBackendStub[Future, R] = SttpBackendStub(new FutureMonad()(ExecutionContext.global))
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class NettyFutureServerStubTest extends ServerStubTest(NettyFutureCreateServerStubTest)
