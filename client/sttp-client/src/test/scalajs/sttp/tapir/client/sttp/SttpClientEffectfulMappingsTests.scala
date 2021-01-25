package sttp.tapir.client.sttp

import sttp.monad.MonadError
import sttp.tapir.client.tests.ClientEffectfulMappingsTests

import scala.concurrent.Future

class SttpClientEffectfulMappingsTests extends SttpClientTests[Any] with ClientEffectfulMappingsTests[Future] {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly
  override implicit def monad: MonadError[Future] = backend.responseMonad

  tests()
}
