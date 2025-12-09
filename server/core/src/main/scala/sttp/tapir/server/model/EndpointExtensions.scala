package sttp.tapir.server.model

import sttp.tapir.EndpointInfoOps
import sttp.tapir.AttributeKey

/** Can be used as an endpoint attribute.
  * @example
  *   {{{
  * endpoint.attribute(MaxContentLength.attributeKey, MaxContentLength(16384L))
  *   }}}
  */
case class MaxContentLength(value: Long) extends AnyVal

object MaxContentLength {
  val attributeKey: AttributeKey[MaxContentLength] = AttributeKey[MaxContentLength]
}

object EndpointExtensions {

  implicit class RichServerEndpoint[E <: EndpointInfoOps[?]](e: E) {

    /** Enables checks that prevent loading full request body into memory if it exceeds given limit. Otherwise causes endpoint to reply with
      * HTTP 413 Payload Too Loarge.
      *
      * Please refer to Tapir docs to ensure which backends are supported: https://tapir.softwaremill.com/en/latest/endpoint/security.html
      * @example
      *   {{{
      * endpoint.maxRequestBodyLength(16384L)
      *   }}}
      * @param maxBytes
      *   maximum allowed size of request body in bytes.
      */
    def maxRequestBodyLength(maxBytes: Long): E =
      e.attribute(MaxContentLength.attributeKey, MaxContentLength(maxBytes)).asInstanceOf[E]
  }
}
