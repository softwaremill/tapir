package sttp.tapir.swagger

/** @param pathPrefix
  *   The path prefix which will be added to the documentation endpoints, as a list of path segments. Defaults to `List("docs")`, so the
  *   address of the docs will be `/docs` (unless `contextPath` is non-empty).
  * @param yamlName
  *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param contextPath
  *   The context path in which the documentation routes are going to be attached. Unless the endpoints are attached to `/`, this needs to
  *   be specified for redirects and yaml reference to work correctly. E.g. when context path is `List("api", "v1")`, and other parameters
  *   are left with default values, the generated full path to the yaml will be `/api/v1/docs/docs.yaml`. Defaults to `Nil`.
  */
case class SwaggerUIOptions(pathPrefix: List[String], yamlName: String, contextPath: List[String]) {
  def pathPrefix(pathPrefix: List[String]): SwaggerUIOptions = copy(pathPrefix = pathPrefix)
  def yamlName(yamlName: String): SwaggerUIOptions = copy(yamlName = yamlName)
  def contextPath(contextPath: List[String]): SwaggerUIOptions = copy(contextPath = contextPath)
}

object SwaggerUIOptions {
  val default: SwaggerUIOptions = SwaggerUIOptions(List("docs"), "docs.yaml", Nil)
}
