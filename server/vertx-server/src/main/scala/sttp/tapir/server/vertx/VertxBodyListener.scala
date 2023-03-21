package sttp.tapir.server.vertx

import io.vertx.core.Future
import io.vertx.ext.web.RoutingContext
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.vertx.interpreters.RunAsync

import scala.util.{Failure, Success, Try}

class VertxBodyListener[F[_]](runAsync: RunAsync[F])(implicit m: MonadError[F]) extends BodyListener[F, RoutingContext => Future[Void]] {
  override def onComplete(body: RoutingContext => Future[Void])(cb: Try[Unit] => F[Unit]): F[RoutingContext => Future[Void]] = {
    m.unit {
      { (ctx: RoutingContext) =>
        // Unfortunately I can not find more reliable way to define that this request is actually websocket.
        // When this code is called server response is not yet written.
        if (ctx.request().getHeader("Upgrade") == "websocket") {
          Future.succeededFuture(runAsync(cb(Success(())))).flatMap(_ => body(ctx))
        } else {
          body {
            ctx.addBodyEndHandler(_ => runAsync(cb(Success(()))))
            ctx.addEndHandler(res => if (res.failed()) runAsync(cb(Failure(res.cause()))))
            ctx
          }
        }
      }
    }
  }
}
