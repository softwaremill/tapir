package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.{Cause, RIO, ZIO}
import zio.stream.ZStream

import scala.util.{Failure, Success, Try}

private[ziohttp] class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZioResponseBody] {
  override def onComplete(body: ZioResponseBody)(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZioResponseBody] =
    ZIO
      .environmentWithZIO[R]
      .apply { r =>
        def succeed = cb(Success(())).provideEnvironment(r)
        def failed(cause: Cause[Throwable]) = cb(Failure(cause.squash)).orDie.provideEnvironment(r)

        body match {
          case Right(ZioStreamHttpResponseBody(stream, contentLength)) =>
            ZIO.right(
              ZioStreamHttpResponseBody(
                stream.onError(failed) ++ ZStream.fromZIO(succeed).drain,
                contentLength
              )
            )
          case rawOrWs => succeed.as(rawOrWs)
        }
      }
}
