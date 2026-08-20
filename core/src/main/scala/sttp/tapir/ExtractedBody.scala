package sttp.tapir

import java.io.InputStream
import java.nio.ByteBuffer
import scala.annotation.implicitNotFound

/** Attribute value marking a body input as extracted: decoded from the request on the server, but not part of the API
  * contract. Extracted bodies are excluded from documentation and ignored by client interpreters, which allows the
  * request body to be decoded more than once - e.g. in `serverSecurityLogic` and again in the main logic.
  *
  * Set using [[Tapir.extractBodyFromRequest]].
  */
case class ExtractedBody()

object ExtractedBody {
  val attributeKey: AttributeKey[ExtractedBody] = new AttributeKey[ExtractedBody]("sttp.tapir.ExtractedBody")
}

/** Evidence that a raw body type can be re-read from buffered bytes, and is therefore usable as an extracted body. */
@implicitNotFound(
  "Cannot use a body with raw type ${R} as an extracted body. Only bodies which can be re-read from buffered bytes " +
    "are supported: string, byte array, byte buffer, input stream. File, multipart and streaming bodies cannot be " +
    "read twice."
)
trait ReplayableRawBody[R]

object ReplayableRawBody {
  private val instance: ReplayableRawBody[Any] = new ReplayableRawBody[Any] {}
  private def of[R]: ReplayableRawBody[R] = instance.asInstanceOf[ReplayableRawBody[R]]

  implicit val forString: ReplayableRawBody[String] = of
  implicit val forByteArray: ReplayableRawBody[Array[Byte]] = of
  implicit val forByteBuffer: ReplayableRawBody[ByteBuffer] = of
  implicit val forInputStream: ReplayableRawBody[InputStream] = of
  implicit val forInputStreamRange: ReplayableRawBody[InputStreamRange] = of
}
