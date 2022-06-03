package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.{RIO, ZIO}
import zio.stream.ZStream

import scala.util.{Failure, Success, Try}

class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZioHttpResponseBody] {
  override def onComplete(body: ZioHttpResponseBody)(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZioHttpResponseBody] =
    ZIO
      .environmentWith[R]
      .apply { r =>
        val (stream, contentLength) = body
        (
          stream.onError(cause => cb(Failure(cause.squash)).orDie.provideEnvironment(r)) ++ ZStream
            .fromZIO(cb(Success(())))
            .provideEnvironment(r)
            .drain,
          contentLength
        )
      }
}
