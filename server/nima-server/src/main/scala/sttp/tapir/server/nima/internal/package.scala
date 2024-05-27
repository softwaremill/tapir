package sttp.tapir.server.nima

import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

package object internal {
  private[nima] implicit val idMonad: MonadError[Identity] = IdentityMonad
}
