package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.model.ServerRequest
import sttp.tapir.{FileRange, RawBodyType, RawPart}

import java.nio.charset.Charset
import scala.util.Try

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]]
  def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream
}

object RequestBody {

  /** The charset from the request's `Content-Type` header, if it's given and supported. [[RequestBody.toRaw]] should decode a whole request
    * body as a [[RawBodyType.StringBody]] using this charset, or else the body type's one.
    */
  def requestCharset(serverRequest: ServerRequest): Option[Charset] =
    serverRequest.contentTypeParsed.flatMap(_.charset).flatMap(c => Try(Charset.forName(c)).toOption)
}

/** @param value
  *   The parsed value of the request body.
  * @param createdFiles
  *   Temporary files created when parsing the request body, which should be deleted when the request is processed.
  * @param cleanup
  *   Cleanup function to be called when the request is processed.
  */
case class RawValue[R](value: R, createdFiles: Seq[FileRange] = Nil, cleanup: Option[() => Unit] = None)

object RawValue {
  def fromParts(parts: Seq[RawPart]): RawValue[Seq[RawPart]] =
    RawValue(parts, parts collect { case _ @Part(_, f: FileRange, _, _) => f })
}
