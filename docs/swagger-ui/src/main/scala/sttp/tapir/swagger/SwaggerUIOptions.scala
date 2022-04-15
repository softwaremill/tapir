package sttp.tapir.swagger

/** @param pathPrefix
  *   The path prefix which will be added to the documentation endpoints, as a list of path segments. Defaults to `List("docs")`, so the
  *   address of the docs will be `./docs` (relative to the context in which the interpreted docs endpoints are attached, and unless
  *   `contextPath` is non-empty).
  * @param yamlName
  *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param contextPath
  *   The optional context path in which the interpreted documentation endpoints are going to be attached. Unless the endpoints are attached
  *   to `/`, this can be specified to create absolute (instead of relative) yaml references and redirects. E.g. when context path is
  *   `List("api", "v1")`, and other parameters are left with default values, the generated full path to the yaml will be
  *   `/api/v1/docs/docs.yaml`, instead of `./docs/docs.yaml`. Defaults to `Nil`.
  */
case class SwaggerUIOptions(pathPrefix: List[String], yamlName: String, contextPath: List[String]) {
  def pathPrefix(pathPrefix: List[String]): SwaggerUIOptions = copy(pathPrefix = pathPrefix)
  def yamlName(yamlName: String): SwaggerUIOptions = copy(yamlName = yamlName)
  def contextPath(contextPath: List[String]): SwaggerUIOptions = copy(contextPath = contextPath)
}

object SwaggerUIOptions {
  val default: SwaggerUIOptions = SwaggerUIOptions(List("docs"), "docs.yaml", Nil)
}
