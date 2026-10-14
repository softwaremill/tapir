package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.model.ServerRequest
import sttp.tapir.{FileRange, RawBodyType, RawPart}

import java.nio.charset.Charset

trait RequestBody[F[_], S] {
  val streams: Streams[S]
  def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]]
  def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream

  /** Decodes `bytes`, the whole request body, into the value which [[toRaw]] returns for a [[RawBodyType.StringBody]] whose codec specifies
    * `defaultCharset`. Backends which don't decode with the codec's charset, or which transform the text (e.g. strip a byte order mark),
    * override this to match.
    */
  def decodeStringBody(serverRequest: ServerRequest, bytes: Array[Byte], defaultCharset: Charset): String =
    new String(bytes, defaultCharset)
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
