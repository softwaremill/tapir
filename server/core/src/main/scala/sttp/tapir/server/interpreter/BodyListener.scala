package sttp.tapir.server.interpreter

import scala.util.Try

trait BodyListener[F[_], B] {
  def onComplete(body: B)(cb: Try[Unit] => F[Unit]): F[B]
}

object BodyListener {
  implicit class BodyListenerOps[B](body: B) {
    def onComplete[F[_]](cb: Try[Unit] => F[Unit])(implicit l: BodyListener[F, B]): F[B] = l.onComplete(body)(cb)
  }
}
