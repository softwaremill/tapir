package sttp.tapir.server.vertx

import io.vertx.ext.web.RoutingContext
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Success, Try}

class VertxBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, RoutingContext => Unit] {
  override def onComplete(body: RoutingContext => Unit)(cb: Try[Unit] => F[Unit]): F[RoutingContext => Unit] = {
    m.unit {
      { (ctx: RoutingContext) =>
        body {
          ctx.addBodyEndHandler(_ => cb(Success(())))
          ctx
        }
      }
    }
  }
}
