package sttp.tapir.server.akkahttp

import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.tests.ServerStubInterpreterTest

import scala.concurrent.Future

class AkkaHttpServerStubTest extends ServerStubInterpreterTest[Future, AkkaStreams with WebSockets, AkkaHttpServerOptions] {
  override def customInterceptors: CustomInterceptors[Future, AkkaHttpServerOptions] =
    AkkaHttpServerOptions.customInterceptors.serverLog(None)
  override def monad: MonadError[Future] = new FutureMonad()
  override def asFuture[A]: Future[A] => Future[A] = identity
}
