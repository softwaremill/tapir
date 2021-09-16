package sttp.tapir.server.netty.internal

import io.netty.buffer.ByteBuf
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Success, Try}

class NettyBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, ByteBuf] {
  override def onComplete(body: ByteBuf)(cb: Try[Unit] => F[Unit]): F[ByteBuf] = {
    cb(Success(())).map(_ => body)
  }
}
