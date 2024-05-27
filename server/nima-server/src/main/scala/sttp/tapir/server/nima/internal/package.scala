package sttp.tapir.server.nima

import sttp.monad.MonadError
import sttp.tapir.Id

package object internal {
  private[nima] implicit val idMonad: MonadError[Id] = sttp.tapir.internal.idMonad
}
