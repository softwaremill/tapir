package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.model.ServerRequest
import sttp.tapir.AttributeKey
import sttp.tapir.EndpointInfo
import sttp.tapir.{FileRange, RawBodyType, RawPart}

case class MaxContentLength(value: Long)

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): F[RawValue[R]]
  def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream

}

case class RawValue[R](value: R, createdFiles: Seq[FileRange] = Nil)

object RawValue {
  def fromParts(parts: Seq[RawPart]): RawValue[Seq[RawPart]] =
    RawValue(parts, parts collect { case _ @Part(_, f: FileRange, _, _) => f })
}
