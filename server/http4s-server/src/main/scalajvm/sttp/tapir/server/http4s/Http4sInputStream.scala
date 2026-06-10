package sttp.tapir.server.http4s

import cats.effect.Async
import cats.syntax.functor._
import fs2.Stream

import java.io.InputStream

private[http4s] object Http4sInputStream {
  def toInputStream[F[_]: Async](body: Stream[F, Byte]): F[InputStream] =
    fs2.io.toInputStreamResource(body).allocated.map { case (is, _) => is }
}
