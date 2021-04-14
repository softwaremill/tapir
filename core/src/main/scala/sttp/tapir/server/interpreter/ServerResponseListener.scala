package sttp.tapir.server.interpreter

import sttp.tapir.model.ServerResponse

trait ServerResponseListener[F[_], B] {
  def listen(r: ServerResponse[B])(cb: => F[Unit]): ServerResponse[B]
}

object ServerResponseListenerSyntax {
  implicit class ResponseBodyListenerOps[B](r: ServerResponse[B]) {
    def listen[F[_]](cb: => F[Unit])(implicit l: ServerResponseListener[F, B]): ServerResponse[B] = l.listen(r)(cb)
  }
}
