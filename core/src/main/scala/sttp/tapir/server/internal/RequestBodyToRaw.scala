package sttp.tapir.server.internal

import sttp.tapir.RawBodyType

trait RequestBodyToRaw[F[_]] {
  def apply[R](bodyType: RawBodyType[R]): F[R]
}
