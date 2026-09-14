package sttp.tapir

import java.io.InputStream
import java.nio.ByteBuffer
import scala.annotation.implicitNotFound

/** Attribute value marking a body input as a secondary definition: decoded from the request on the server, but not part of the API
  * contract. Secondary bodies are excluded from documentation and ignored by client interpreters, which allows the request body to be
  * decoded more than once - e.g. in `serverSecurityLogic` and again in the main logic.
  *
  * Set using [[EndpointIO.Body.asSecondary]].
  */
case class SecondaryBody()

object SecondaryBody {
  val attributeKey: AttributeKey[SecondaryBody] = new AttributeKey[SecondaryBody]("sttp.tapir.SecondaryBody")
}

/** Evidence that a raw body type can be re-read from buffered bytes, and is therefore usable as a secondary body. */
@implicitNotFound(
  "Cannot use a body with raw type ${R} as a secondary body. Only bodies which can be re-read from buffered bytes " +
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
