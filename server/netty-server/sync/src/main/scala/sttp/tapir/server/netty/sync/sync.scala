package sttp.tapir.server.netty

import sttp.monad.MonadError
import sttp.tapir.Id

package object sync:
  type IdRoute = Route[Id]
  private[sync] implicit val idMonad: MonadError[Id] = sttp.tapir.internal.idMonad
