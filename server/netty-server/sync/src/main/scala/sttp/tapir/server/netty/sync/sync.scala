package sttp.tapir.server.netty

import sttp.monad.{MonadError, IdentityMonad}
import sttp.shared.Identity

package object sync:
  type IdRoute = Route[Identity]
  private[sync] implicit val idMonad: MonadError[Identity] = IdentityMonad
