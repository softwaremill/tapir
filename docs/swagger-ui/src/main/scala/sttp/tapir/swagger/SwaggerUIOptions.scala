package sttp.tapir.swagger

/** @param pathPrefix
  *   The path prefix which will be added to the documentation endpoints, as a list of path segments. Defaults to `List("docs")`, so the
  *   address of the docs will be `./docs` (relative to the context in which the interpreted docs endpoints are attached).
  * @param yamlName
  *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param contextPath
  *   The context path in which the interpreted documentation endpoints are going to be attached. Only relevant when {{useRelativePaths ==
  *   false}}. E.g. when context path is `List("api", "v1")`, `useRelativePaths = false`, and other parameters are left with default values,
  *   the generated full path to the yaml will be `/api/v1/docs/docs.yaml`, instead of `./docs/docs.yaml` or `./docs.yaml` (depending on the
  *   referrer's uri). Also used for creating redirects. Defaults to `Nil`.
  * @param useRelativePaths
  *   Should relative paths be used for yaml references and redirects. Defaults to `true`.
  * @param showExtensions
  *   Should display the content of vendor extensions (x-) fields and values for Operations, Parameters, Responses, and Schema. Defaults to
  *   `false`.
  */
case class SwaggerUIOptions(
    pathPrefix: List[String],
    yamlName: String,
    contextPath: List[String],
    useRelativePaths: Boolean,
    showExtensions: Boolean
) {
  def pathPrefix(pathPrefix: List[String]): SwaggerUIOptions = copy(pathPrefix = pathPrefix)
  def yamlName(yamlName: String): SwaggerUIOptions = copy(yamlName = yamlName)
  def contextPath(contextPath: List[String]): SwaggerUIOptions = copy(contextPath = contextPath)
  def withRelativePaths: SwaggerUIOptions = copy(useRelativePaths = true)
  def withAbsolutePaths: SwaggerUIOptions = copy(useRelativePaths = false)
  def withShowExtensions: SwaggerUIOptions = copy(showExtensions = true)
  def withHideExtensions: SwaggerUIOptions = copy(showExtensions = false)
}

object SwaggerUIOptions {
  val default: SwaggerUIOptions = SwaggerUIOptions(List("docs"), "docs.yaml", Nil, useRelativePaths = true, showExtensions = false)
}
