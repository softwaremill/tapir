package sttp.tapir.server.nima

import sttp.monad.MonadError
import sttp.tapir.Identity

package object internal {
  private[nima] implicit val idMonad: MonadError[Identity] = sttp.tapir.internal.idMonad
}
