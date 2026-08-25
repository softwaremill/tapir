package sttp.tapir.docs.openapi

import sttp.tapir.{AttributeKey, EndpointTransput}

/** Marks a parameter or header as a reusable OpenAPI component: instead of being serialised in full into every operation that uses it, it
  * is emitted once into the `components` section and referenced with a `$ref` from each use site.
  *
  * @param name
  *   the key to emit the component under; when empty, the parameter's or header's own name is used.
  */
case class ReusableComponent(name: Option[String])

object ReusableComponentAttribute {

  /** The key under which the marker is stored on a parameter or header. Set it through [[RichBasicEndpointTransput]] rather than directly.
    */
  val reusableComponentAttributeKey: AttributeKey[ReusableComponent] = AttributeKey[ReusableComponent]

  implicit class RichBasicEndpointTransput[E <: EndpointTransput.Atom[_]](e: E) {

    /** Emits this parameter or header once into the `components` section, referenced from every operation that uses it, under a key which
      * is the parameter's or header's own name.
      *
      * Use when the same parameter is shared by many endpoints, to avoid repeating it in full in each operation.
      *
      * See https://tapir.softwaremill.com/en/latest/docs/openapi.html for details.
      */
    def reusableComponent: E = e.attribute(reusableComponentAttributeKey, ReusableComponent(None)).asInstanceOf[E]

    /** As [[reusableComponent]], but emits the component under the given key instead of the parameter's or header's own name. Needed when
      * two different components would otherwise claim the same key.
      */
    def reusableComponent(name: String): E =
      e.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name))).asInstanceOf[E]

    /** The reusable-component marker set on this parameter or header, if any. */
    def reusableComponentMarker: Option[ReusableComponent] = e.attribute(reusableComponentAttributeKey)
  }
}
