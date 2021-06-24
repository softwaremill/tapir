package sttp.tapir.server.zhttp

import sttp.tapir.server.interpreter.BodyListener
import zio.RIO
import zio.blocking.Blocking
import zio.stream.ZStream

import scala.util.Try


class ZHttpBodyListener[R] extends BodyListener[RIO[R, *], ZStream[Blocking, Throwable, Byte]] {
  override def onComplete(body: ZStream[Blocking, Throwable, Byte])(cb: Try[Unit] => RIO[R, Unit]): RIO[R, ZStream[Blocking, Throwable, Byte]] = ???
}
