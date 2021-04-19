package sttp.tapir.server.vertx

import io.vertx.ext.web.RoutingContext
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener

class VertxBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, RoutingContext => Unit] {
  override def onComplete(body: RoutingContext => Unit)(cb: => F[Unit]): F[RoutingContext => Unit] = {
    m.unit {
      { (ctx: RoutingContext) =>
        body {
          ctx.addBodyEndHandler(_ => cb)
          ctx
        }
      }
    }
  }
}
