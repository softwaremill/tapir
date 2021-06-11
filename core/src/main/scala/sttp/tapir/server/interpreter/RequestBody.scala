package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]]
  def toStream(): streams.BinaryStream
}

case class RawValue[R](value: R, createdFiles: Seq[TapirFile] = Nil)

object RawValue {
  def fromParts(parts: Seq[RawPart]): RawValue[Seq[RawPart]] =
    RawValue(parts, parts collect { case _ @Part(_, f: TapirFile, _, _) => f })
}
