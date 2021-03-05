package sttp.tapir.server.internal

import sttp.capabilities.Streams
import sttp.tapir.RawBodyType

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](bodyType: RawBodyType[R]): F[R]
  def toStream(): streams.BinaryStream
}
