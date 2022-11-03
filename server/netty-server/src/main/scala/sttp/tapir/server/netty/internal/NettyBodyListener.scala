package sttp.tapir.server.netty.internal

import io.netty.channel.ChannelHandlerContext
import io.netty.util.concurrent.Future
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.netty.NettyResponse

import scala.util.{Failure, Success, Try}

class NettyBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, NettyResponse] {
  override def onComplete(body: NettyResponse)(cb: Try[Unit] => F[Unit]): F[NettyResponse] = {
    m.eval((ctx: ChannelHandlerContext) => {
      val (promise, choice3) = body(ctx)
      promise.addListener((future: Future[_ >: Void]) => {
        if (future.isSuccess) {
          cb(Success(()))
        } else {
          cb(Failure(future.cause()))
        }
      })

      (promise, choice3)
    })
  }
}
