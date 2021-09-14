package sttp.tapir.server.netty.internal

import io.netty.buffer.ByteBuf
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Success, Try}

class NettyBodyListener[Future[_]](implicit m: MonadError[Future]) extends BodyListener[Future, ByteBuf] {
  override def onComplete(body: ByteBuf)(cb: Try[Unit] => Future[Unit]): Future[ByteBuf] = {
    cb(Success(())).map(_ => body)
  }
}
