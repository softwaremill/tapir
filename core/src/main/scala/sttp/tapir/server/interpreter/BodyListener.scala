package sttp.tapir.server.interpreter

trait BodyListener[F[_], B] {
  def onComplete(body: B)(cb: => F[Unit]): F[B]
}

object BodyListenerSyntax {
  implicit class BodyListenerOps[B](body: B) {
    def onComplete[F[_]](cb: => F[Unit])(implicit l: BodyListener[F, B]): F[B] = l.onComplete(body)(cb)
  }
}
