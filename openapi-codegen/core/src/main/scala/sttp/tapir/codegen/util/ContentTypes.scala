package sttp.tapir.codegen.util

object ContentTypes {
  private val jsonContentType = "application/(.+\\+)?json".r

  /** `application/json`, or a structured syntax suffix type such as `application/problem+json`. */
  def isJson(contentType: String): Boolean = jsonContentType.pattern.matcher(contentType).matches

  private val xmlContentType = "application/(.+\\+)?xml".r

  /** `application/xml`, or a structured syntax suffix type such as `application/problem+xml`. */
  def isXml(contentType: String): Boolean = xmlContentType.pattern.matcher(contentType).matches

  /** A `+json` / `+xml` type, e.g. `application/problem+json`. */
  def isSuffixed(contentType: String): Boolean =
    isJsonOrXml(contentType) && contentType != "application/json" && contentType != "application/xml"

  private def isJsonOrXml(contentType: String): Boolean = isJson(contentType) || isXml(contentType)

  private val nativeContentTypes = Set("text/plain", "text/html", "multipart/form-data", "application/octet-stream")

  /** True if no top-level codec format class needs to be generated for the content type. */
  def isNativeContentType(contentType: String): Boolean = nativeContentTypes.contains(contentType) || isJsonOrXml(contentType)

  /** True if the body can be mapped to a generated class. */
  def isClassMappable(contentType: String): Boolean = contentType == "multipart/form-data" || isJsonOrXml(contentType)

  // '*/*' has no schema mappings, but we default to eager for convenience
  private val eagerContentTypes = Set("text/plain", "text/html", "multipart/form-data", "*/*")

  /** True if the body is read into memory by default. When any body variant of a `oneOfBody` is eager, all variants are generated as eager.
    */
  def isEager(contentType: String): Boolean = eagerContentTypes.contains(contentType) || isJsonOrXml(contentType)

  private val mediaType = "([^/]+)/(.+)".r

  /** A Scala expression creating the `sttp.model.MediaType` of the content type. */
  def mediaTypeExpr(contentType: String): String = contentType match {
    case mediaType(mainType, subType) =>
      val (main, sub) = (JavaEscape.escapeString(mainType), JavaEscape.escapeString(subType))
      s"""sttp.model.MediaType.unsafeApply(mainType = "$main", subType = "$sub")"""
    case ct => throw new NotImplementedError(s"Cannot handle content type '$ct'")
  }
}
