package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.RIO
import zio.blocking.Blocking
import zio.stream.ZStream

import scala.util.{Failure, Success, Try}


class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZStream[Blocking, Throwable, Byte]] {
  override def onComplete(body: ZStream[Blocking, Throwable, Byte])(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZStream[Blocking, Throwable, Byte]] =
    RIO.access[R].apply(r => body.onError(cause => cb(Failure(cause.squash)).orDie.provide(r)) ++ ZStream.fromEffect(cb(Success(()))).provide(r).drain)
}
