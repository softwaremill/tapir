package sttp.tapir.server.netty

import sttp.monad.MonadError
import sttp.tapir.Identity

package object sync:
  type IdRoute = Route[Identity]
  private[sync] implicit val idMonad: MonadError[Identity] = sttp.tapir.internal.idMonad
