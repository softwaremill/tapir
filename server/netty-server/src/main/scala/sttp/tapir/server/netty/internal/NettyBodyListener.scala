package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpChunkedInput
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Success, Try}

class NettyBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, HttpChunkedInput] {
  override def onComplete(body: HttpChunkedInput)(cb: Try[Unit] => F[Unit]): F[HttpChunkedInput] = {
    cb(Success(())).map(_ => body)
  }
}
