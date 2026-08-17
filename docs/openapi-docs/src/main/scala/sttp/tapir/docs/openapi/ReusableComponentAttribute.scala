package sttp.tapir.docs.openapi

import sttp.tapir.{AttributeKey, EndpointTransput}

/** Marks an endpoint input or output atom as reusable: instead of being inlined at every use site, it is emitted once into the OpenAPI
  * `components` section and referenced with a `$ref`.
  *
  * Which section it lands in is inferred from position, not declared: an atom reached while walking an endpoint's inputs becomes a
  * `components/parameters` entry (including request headers, which OpenAPI models as parameters with `in: header`); one reached while
  * walking its outputs becomes a `components/headers` entry.
  *
  * @param name
  *   the key to emit the component under. When `None`, the parameter's or header's own name is used.
  */
case class ReusableComponent(name: Option[String])

object ReusableComponentAttribute {
  val reusableComponentAttributeKey: AttributeKey[ReusableComponent] = AttributeKey[ReusableComponent]

  // E-casts: we know that adding an attribute to an endpoint io doesn't change its type; however, the methods return ThisType[_].
  // An alternative encoding, returning ThisType, fails to infer correctly. Mirrors DocsExtensionAttribute.RichBasicEndpointTransput.
  implicit class RichBasicEndpointTransput[E <: EndpointTransput.Atom[_]](e: E) {

    /** Emit this input/output into `components` under its own name, and reference it from every use site. */
    def reusableComponent: E = e.attribute(reusableComponentAttributeKey, ReusableComponent(None)).asInstanceOf[E]

    /** Emit this input/output into `components` under the given key, and reference it from every use site. */
    def reusableComponent(name: String): E =
      e.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name))).asInstanceOf[E]

    def reusableComponentMarker: Option[ReusableComponent] = e.attribute(reusableComponentAttributeKey)
  }
}
