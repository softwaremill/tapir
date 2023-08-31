package sttp.tapir.server.ziohttp

import sttp.tapir.server.interpreter.BodyListener
import zio.{RIO, ZIO}
import zio.stream.ZStream

import scala.util.{Failure, Success, Try}

private[ziohttp] class ZioHttpBodyListener[R] extends BodyListener[RIO[R, *], ZioResponseBody] {
  override def onComplete(body: ZioResponseBody)(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZioResponseBody] =
    ZIO
      .environmentWithZIO[R]
      .apply { r =>
        body match {
          case Right(ZioStreamHttpResponseBody(stream, contentLength)) =>
            ZIO.right(
              ZioStreamHttpResponseBody(
                stream.onError(cause => cb(Failure(cause.squash)).orDie.provideEnvironment(r)) ++ ZStream
                  .fromZIO(cb(Success(())))
                  .provideEnvironment(r)
                  .drain,
                contentLength
              )
            )
          case raw @ Right(_: ZioRawHttpResponseBody) => cb(Success(())).provideEnvironment(r).map(_ => raw)
          case ws @ Left(_)                           => cb(Success(())).provideEnvironment(r).map(_ => ws)
        }
      }
}
