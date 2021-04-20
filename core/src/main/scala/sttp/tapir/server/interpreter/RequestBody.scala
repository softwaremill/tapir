package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.{RawBodyType, RawPart}

import java.io.File

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]]
  def toStream(): streams.BinaryStream
}

case class RawValue[R](value: R, tmpFiles: Seq[File] = Nil)

object RawValue {
  def fromParts(parts: Seq[RawPart]): RawValue[Seq[RawPart]] =
    RawValue(parts, parts collect { case _ @Part(_, f, _, _) if f.isInstanceOf[File] => f.asInstanceOf[File] })
}
