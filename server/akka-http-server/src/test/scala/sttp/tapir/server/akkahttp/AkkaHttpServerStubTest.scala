package sttp.tapir.server.akkahttp

import sttp.capabilities.WebSockets
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest

import scala.concurrent.Future

class AkkaHttpServerStubTest extends ServerStubInterpreterTest[Future, WebSockets, AkkaHttpServerOptions] {
  override def customInterceptors: CustomInterceptors[Future, AkkaHttpServerOptions] =
    AkkaHttpServerOptions.customInterceptors.serverLog(None)
  override def stub: SttpBackendStub[Future, WebSockets] = SttpBackendStub.asynchronousFuture
  override def asFuture[A]: Future[A] => Future[A] = identity

}
