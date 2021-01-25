package sttp.tapir.client.sttp

import cats.effect.IO
import sttp.monad.MonadError
import sttp.tapir.client.tests.ClientEffectfulMappingsTests

class SttpClientEffectfulMappingsTests extends SttpClientTests[Any] with ClientEffectfulMappingsTests[IO] {
  override def wsToPipe: WebSocketToPipe[Any] = implicitly
  override implicit def monad: MonadError[IO] = backend.responseMonad

  tests()
}
