package sttp.tapir.client.play

import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.client.tests.ClientEffectfulMappingsTests

import scala.concurrent.Future

class PlayClientEffectfulMappingsTests extends PlayClientTests[Any] with ClientEffectfulMappingsTests[Future] {
  override implicit def monad: MonadError[Future] = new FutureMonad()

  tests()
}
