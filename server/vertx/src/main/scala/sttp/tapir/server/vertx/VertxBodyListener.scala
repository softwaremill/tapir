package sttp.tapir.server.vertx

import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.interpreter.BodyListener

class VertxBodyListener[F[_]] extends BodyListener[F, RoutingContext => Unit] {
  override def onComplete(body: RoutingContext => Unit)(cb: => F[Unit]): RoutingContext => Unit = { (ctx: RoutingContext) =>
    body({
      ctx.addBodyEndHandler(_ => cb)
      ctx
    })
  }
}
