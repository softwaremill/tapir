package sttp.tapir.server.netty.internal

import io.netty.channel.ChannelHandlerContext
import io.netty.util.concurrent.Future
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.netty.NettyResponse

import scala.util.{Failure, Success, Try}

class NettyBodyListener[F[_]](runAsync: RunAsync[F])(implicit m: MonadError[F]) extends BodyListener[F, NettyResponse] {
  override def onComplete(body: NettyResponse)(cb: Try[Unit] => F[Unit]): F[NettyResponse] = {
    m.eval((ctx: ChannelHandlerContext) => {
      val nettyResponseContent = body(ctx)
      nettyResponseContent.channelPromise.addListener((future: Future[_ >: Void]) => {
        if (future.isSuccess) {
          runAsync(cb(Success(())))
        } else {
          runAsync(cb(Failure(future.cause())))
        }
      })

      nettyResponseContent
    })
  }
}
