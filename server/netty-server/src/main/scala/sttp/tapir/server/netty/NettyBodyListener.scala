package sttp.tapir.server.netty

import scala.util.{Success, Try}

import io.netty.buffer.ByteBuf
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener

import sttp.monad.syntax._

class NettyBodyListener[Future[_]](implicit m: MonadError[Future]) extends BodyListener[Future, ByteBuf] {
  override def onComplete(body: ByteBuf)(cb: Try[Unit] => Future[Unit]): Future[ByteBuf] = {
    cb(Success(())).map(_ => body)
  }
}
