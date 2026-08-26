package sttp.tapir.codegen.util

object ContentTypes {
  private val jsonContentType = "application/(.+\\+)?json".r

  /** Returns if the content type is a Json content type. Accepts standard application/json, as well as extended application/foo+json
    * content types
    */
  def isJson(contentType: String): Boolean = jsonContentType.pattern.matcher(contentType).matches

  private val xmlContentType = "application/(.+\\+)?xml".r

  /** Returns if the content type is a XML content type. Accepts standard application/xml, as well as extended application/foo+xml content
    * types
    */
  def isXml(contentType: String): Boolean = xmlContentType.pattern.matcher(contentType).matches

  private val nativeContentTypes: Set[String] = Set("text/plain", "text/html", "multipart/form-data", "application/octet-stream")

  /** Returns true if the content type has a native codec in codegen, and does not require a custom codec
    */
  def isNativeContentType(contentType: String): Boolean =
    nativeContentTypes.contains(contentType) || isJson(contentType) || isXml(contentType)

  private val classMappableContentTypes = Set("multipart/form-data")

  /** Returns true if a content type can be mapped to a scala class
    */
  def isClassMappable(contentType: String): Boolean =
    classMappableContentTypes.contains(contentType) || ContentTypes.isJson(contentType) || ContentTypes.isXml(contentType)

  // These types all use 'eager' schemas, except for '*/*', which we default to eager for convenience but which has no schema mappings
  private val eagerContentTypes = Set("text/plain", "text/html", "multipart/form-data", "*/*")

  /** Returns true if the content type is eager
    */
  def isEager(contentType: String): Boolean =
    eagerContentTypes.contains(contentType) || ContentTypes.isJson(contentType) || ContentTypes.isXml(contentType)
}
