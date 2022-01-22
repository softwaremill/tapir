package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.RIO
import zio.stream.ZStream

import scala.util.{Failure, Success, Try}

class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZStream[Any, Throwable, Byte]] {
  override def onComplete(body: ZStream[Any, Throwable, Byte])(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZStream[Any, Throwable, Byte]] =
    RIO
      .environmentWith[R]
      .apply(r =>
        body.onError(cause => cb(Failure(cause.squash)).orDie.provideEnvironment(r)) ++ ZStream
          .fromZIO(cb(Success(())))
          .provideEnvironment(r)
          .drain
      )
}
