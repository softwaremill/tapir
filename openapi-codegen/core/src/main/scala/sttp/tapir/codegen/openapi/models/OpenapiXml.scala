package sttp.tapir.codegen.openapi.models

object OpenapiXml {
  sealed trait XmlConfiguration
  final case class XmlArrayConfiguration(name: Option[String] = None, wrapped: Option[Boolean] = None, itemName: Option[String] = None)
      extends XmlConfiguration {
    def isWrapped: Boolean = wrapped.contains(true)
  }
  final case class XmlObjectConfiguration(name: Option[String], prefix: Option[String], namespace: Option[String]) extends XmlConfiguration
  final case class XmlItemConfiguration(name: Option[String], attribute: Option[Boolean]) extends XmlConfiguration
}
