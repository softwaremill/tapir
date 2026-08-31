package sttp.tapir.docs.openapi

import sttp.tapir.{AttributeKey, EndpointIO, EndpointInput}

/** Marks a parameter or header as a reusable OpenAPI component - where a parameter is a `query`, `path` or `cookie` input, and a header is
  * a request or response header. Instead of being serialised in full into every operation that uses it, it is emitted once into the
  * `components` section and referenced with a `$ref` from each use site.
  *
  * @param name
  *   the key to emit the component under; when empty, the parameter's or header's own name is used.
  */
case class ReusableComponent(name: Option[String])

object ReusableComponentAttribute {

  /** The key under which the marker is stored on a parameter or header. Set it through the `reusableComponent` methods rather than
    * directly.
    */
  val reusableComponentAttributeKey: AttributeKey[ReusableComponent] = AttributeKey[ReusableComponent]

  /** Marks a query parameter as a reusable component, see [[ReusableComponent]]. */
  implicit class RichQuery[T](q: EndpointInput.Query[T]) {
    def reusableComponent: EndpointInput.Query[T] = q.attribute(reusableComponentAttributeKey, ReusableComponent(None))
    def reusableComponent(name: String): EndpointInput.Query[T] =
      q.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name)))
  }

  /** Marks a path parameter as a reusable component, see [[ReusableComponent]]. */
  implicit class RichPathCapture[T](p: EndpointInput.PathCapture[T]) {
    def reusableComponent: EndpointInput.PathCapture[T] = p.attribute(reusableComponentAttributeKey, ReusableComponent(None))
    def reusableComponent(name: String): EndpointInput.PathCapture[T] =
      p.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name)))
  }

  /** Marks a cookie parameter as a reusable component, see [[ReusableComponent]]. */
  implicit class RichCookie[T](c: EndpointInput.Cookie[T]) {
    def reusableComponent: EndpointInput.Cookie[T] = c.attribute(reusableComponentAttributeKey, ReusableComponent(None))
    def reusableComponent(name: String): EndpointInput.Cookie[T] =
      c.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name)))
  }

  /** Marks a header as a reusable component, see [[ReusableComponent]]. */
  implicit class RichHeader[T](h: EndpointIO.Header[T]) {
    def reusableComponent: EndpointIO.Header[T] = h.attribute(reusableComponentAttributeKey, ReusableComponent(None))
    def reusableComponent(name: String): EndpointIO.Header[T] =
      h.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name)))
  }

  /** Marks a fixed header as a reusable component, see [[ReusableComponent]]. */
  implicit class RichFixedHeader[T](h: EndpointIO.FixedHeader[T]) {
    def reusableComponent: EndpointIO.FixedHeader[T] = h.attribute(reusableComponentAttributeKey, ReusableComponent(None))
    def reusableComponent(name: String): EndpointIO.FixedHeader[T] =
      h.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name)))
  }

}
