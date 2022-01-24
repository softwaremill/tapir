package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.RIO
import zio.stream.ZStream

import scala.util.{Failure, Success, Try}

class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZioResponseBody] {
  override def onComplete(body: ZioResponseBody)(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZioResponseBody] =
    body match {
      case ws @ Left(_) => cb(Success(())).map(_ => ws)
      case Right(stream) => RIO
        .access[R]
        .apply(r => Right(stream.onError(cause => cb(Failure(cause.squash)).orDie.provide(r)) ++ ZStream.fromEffect(cb(Success(()))).provide(r).drain))
    }
}
