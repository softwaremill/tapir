package sttp.tapir.server.jdkhttp

import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

package object internal {
  private[jdkhttp] implicit val idMonad: MonadError[Identity] = IdentityMonad
}
