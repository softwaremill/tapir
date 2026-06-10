package sttp.tapir.server.http4s

import cats.effect.Async
import cats.syntax.functor._
import fs2.{Chunk, Stream}

import java.io.{ByteArrayInputStream, InputStream}

private[http4s] object Http4sInputStream {
  def toInputStream[F[_]: Async](body: Stream[F, Byte]): F[InputStream] =
    body.compile.to(Chunk).map(c => new ByteArrayInputStream(c.toArray[Byte]))
}
