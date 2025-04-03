package sttp.tapir.server.netty

import sttp.client4.testing.BackendStub
import sttp.monad.FutureMonad
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.tests.{CreateServerStubTest, ServerStubTest}

import scala.concurrent.{ExecutionContext, Future}

object NettyFutureCreateServerStubTest extends CreateServerStubTest[Future, NettyFutureServerOptions] {
  override def customiseInterceptors: CustomiseInterceptors[Future, NettyFutureServerOptions] =
    NettyFutureServerOptions.customiseInterceptors
  override def stub: BackendStub[Future] = BackendStub(new FutureMonad()(ExecutionContext.global))
  override def asFuture[A]: Future[A] => Future[A] = identity
}

class NettyFutureServerStubTest extends ServerStubTest(NettyFutureCreateServerStubTest)
