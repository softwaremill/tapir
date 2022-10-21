package sttp.tapir.server.netty.internal

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.netty.NettyResponse

import scala.util.{Success, Try}

class NettyBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, NettyResponse] {
  override def onComplete(body: NettyResponse)(cb: Try[Unit] => F[Unit]): F[NettyResponse] = {
    cb(Success(())).map(_ => body)
  }
}
