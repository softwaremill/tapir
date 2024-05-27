package sttp.tapir.server.jdkhttp

import sttp.monad.MonadError
import sttp.tapir.Id

package object internal {
  private[jdkhttp] implicit val idMonad: MonadError[Id] = sttp.tapir.internal.idMonad
}
