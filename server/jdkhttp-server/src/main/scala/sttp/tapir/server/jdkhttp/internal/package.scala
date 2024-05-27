package sttp.tapir.server.jdkhttp

import sttp.monad.MonadError
import sttp.tapir.Identity

package object internal {
  private[jdkhttp] implicit val idMonad: MonadError[Identity] = sttp.tapir.internal.idMonad
}
