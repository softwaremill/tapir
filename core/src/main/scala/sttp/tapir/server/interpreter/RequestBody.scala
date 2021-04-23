package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.model.SttpFile
import sttp.tapir.{RawBodyType, RawPart}

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]]
  def toStream(): streams.BinaryStream
}

case class RawValue[R](value: R, createdFiles: Seq[SttpFile] = Nil)

object RawValue {
  def fromParts(parts: Seq[RawPart]): RawValue[Seq[RawPart]] =
    RawValue(parts, parts collect { case _ @Part(_, f: SttpFile, _, _) => f })
}
