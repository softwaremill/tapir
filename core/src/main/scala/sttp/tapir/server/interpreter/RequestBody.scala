package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.tapir.RawBodyType

import java.io.File

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]]
  def toStream(): streams.BinaryStream
}

case class RawValue[R](value: R, tmpFiles: Seq[File] = Nil)

