package sttp.tapir.docs.openapi

import sttp.tapir.{AttributeKey, EndpointTransput}

case class ReusableComponent(name: Option[String])

object ReusableComponentAttribute {
  val reusableComponentAttributeKey: AttributeKey[ReusableComponent] = AttributeKey[ReusableComponent]

  implicit class RichBasicEndpointTransput[E <: EndpointTransput.Atom[_]](e: E) {

    def reusableComponent: E = e.attribute(reusableComponentAttributeKey, ReusableComponent(None)).asInstanceOf[E]

    def reusableComponent(name: String): E =
      e.attribute(reusableComponentAttributeKey, ReusableComponent(Some(name))).asInstanceOf[E]

    def reusableComponentMarker: Option[ReusableComponent] = e.attribute(reusableComponentAttributeKey)
  }
}
