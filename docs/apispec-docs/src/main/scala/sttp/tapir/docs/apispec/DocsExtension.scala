package sttp.tapir.docs.apispec

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.{AttributeKey, EndpointIO, EndpointInfo, EndpointInfoOps, EndpointTransput, WebSocketBodyOutput}

case class DocsExtension[A](key: String, value: A, codec: JsonCodec[A]) {
  def rawValue: String = codec.encode(value)
}
object DocsExtension {
  def of[A](key: String, value: A)(implicit codec: JsonCodec[A]): DocsExtension[A] = DocsExtension(key, value, codec)
}

object DocsExtensionAttribute {
  val docsExtensionAttributeKey: AttributeKey[Vector[DocsExtension[_]]] = AttributeKey[Vector[DocsExtension[_]]]

  //

  implicit class RichEndpointIOInfo[T](i: EndpointIO.Info[T]) {
    def docsExtension[D: JsonCodec](key: String, value: D): EndpointIO.Info[T] =
      i.attribute(docsExtensionAttributeKey, docsExtensions :+ DocsExtension.of(key, value))
    def docsExtensions: Vector[DocsExtension[_]] = i.attribute(docsExtensionAttributeKey).getOrElse(Vector.empty)
  }

  implicit class RichEndpointInfo(i: EndpointInfo) {
    def docsExtension[D: JsonCodec](key: String, value: D): EndpointInfo =
      i.attribute(docsExtensionAttributeKey, docsExtensions :+ DocsExtension.of(key, value))
    def docsExtensions: Vector[DocsExtension[_]] = i.attribute(docsExtensionAttributeKey).getOrElse(Vector.empty)
  }

  //

  // E-casts: we know that adding an attribute to an endpoint/endpoint io doesn't change its type; however, the methods
  // return ThisType[_]. An alternative encoding, returning ThisType, fails to infer correctly.

  implicit class RichEndpointInfoOps[E <: EndpointInfoOps[_]](e: E) {
    def docsExtension[D: JsonCodec](key: String, value: D): E =
      e.attribute(docsExtensionAttributeKey, docsExtensions :+ DocsExtension.of(key, value)).asInstanceOf[E]

    def docsExtensions: Vector[DocsExtension[_]] = e.attribute(docsExtensionAttributeKey).getOrElse(Vector.empty)
  }

  implicit class RichBasicEndpointTransput[E <: EndpointTransput.Basic[_]](e: E) {
    def docsExtension[D: JsonCodec](key: String, value: D): E =
      e.attribute(docsExtensionAttributeKey, docsExtensions :+ DocsExtension.of(key, value)).asInstanceOf[E]

    def docsExtensions: Vector[DocsExtension[_]] = e.attribute(docsExtensionAttributeKey).getOrElse(Vector.empty)
  }

  implicit class RichWebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S](b: WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S]) {
    def requestsDocsExtension[A: JsonCodec](key: String, value: A): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] =
      b.copy(requestsInfo = b.requestsInfo.docsExtension(key, value))
    def responsesDocsExtension[A: JsonCodec](key: String, value: A): WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S] =
      b.copy(responsesInfo = b.responsesInfo.docsExtension(key, value))
  }

}
