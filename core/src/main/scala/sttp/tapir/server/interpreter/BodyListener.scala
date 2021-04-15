package sttp.tapir.server.interpreter

trait BodyListener[F[_], B] {
  def listen(body: B)(cb: => F[Unit]): B
}

object BodyListenerSyntax {
  implicit class BodyListenerOps[B](body: B) {
    def listen[F[_]](cb: => F[Unit])(implicit l: BodyListener[F, B]): B = l.listen(body)(cb)
  }
}
