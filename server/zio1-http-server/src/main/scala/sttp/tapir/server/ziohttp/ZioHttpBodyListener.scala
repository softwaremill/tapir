package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.RIO
import zio.stream.ZStream

import scala.util.{Failure, Success, Try}

class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZioHttpResponseBody] {
  override def onComplete(body: ZioHttpResponseBody)(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZioHttpResponseBody] =
    RIO
      .access[R]
      .apply { r =>
        val (stream, contentLength) = body
        (
          stream.onError(cause => cb(Failure(cause.squash)).orDie.provide(r)) ++ ZStream.fromEffect(cb(Success(()))).provide(r).drain,
          contentLength
        )
      }
}
