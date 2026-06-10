package sttp.tapir.scalar

/** @param pathPrefix
  *   The path prefix which will be added to the documentation endpoints, as a list of path segments. Defaults to `List("docs")`, so the
  *   address of the docs will be `./docs` (relative to the context in which the interpreted docs endpoints are attached).
  * @param specName
  *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param htmlName
  *   the name of the file, through which the html documentation will be served. Defaults to `index.html`.
  * @param contextPath
  *   The context path in which the interpreted documentation endpoints are going to be attached. Only relevant when {{useRelativePaths ==
  *   false}}. E.g. when context path is `List("api", "v1")`, `useRelativePaths = false`, and other parameters are left with default values,
  *   the generated full path to the yaml will be `/api/v1/docs/docs.yaml`, instead of `./docs/docs.yaml` or `./docs.yaml` (depending on the
  *   referrer's uri). Also used for creating redirects. Defaults to `Nil`.
  * @param useRelativePaths
  *   Should relative paths be used for yaml references and redirects. Defaults to `true`.
  * @param scalarVersion
  *   Optional version of the Scalar library. When set, the versioned CDN URL
  *   `https://cdn.jsdelivr.net/npm/@scalar/api-reference@{version}/dist/browser/standalone.js` is used. When `None`, the unversioned URL
  *   `https://cdn.jsdelivr.net/npm/@scalar/api-reference` (latest) is used.
  * @param scalarConfiguration
  *   Optional Scalar UI configuration options (see [[ScalarConfiguration]]).
  */
case class ScalarUIOptions(
    pathPrefix: List[String],
    specName: String,
    htmlName: String,
    contextPath: List[String],
    useRelativePaths: Boolean,
    scalarVersion: Option[String],
    scalarConfiguration: ScalarConfiguration
) {
  def pathPrefix(pathPrefix: List[String]): ScalarUIOptions = copy(pathPrefix = pathPrefix)
  def specName(specName: String): ScalarUIOptions = copy(specName = specName)
  def htmlName(htmlName: String): ScalarUIOptions = copy(htmlName = htmlName)
  def contextPath(contextPath: List[String]): ScalarUIOptions = copy(contextPath = contextPath)
  def withRelativePaths: ScalarUIOptions = copy(useRelativePaths = true)
  def withAbsolutePaths: ScalarUIOptions = copy(useRelativePaths = false)
  def scalarVersion(scalarVersion: String): ScalarUIOptions = copy(scalarVersion = Some(scalarVersion))
  def scalarConfiguration(scalarConfiguration: ScalarConfiguration): ScalarUIOptions = copy(scalarConfiguration = scalarConfiguration)
}

object ScalarUIOptions {
  val default: ScalarUIOptions =
    ScalarUIOptions(List("docs"), "docs.yaml", "index.html", Nil, useRelativePaths = true, None, ScalarConfiguration.default)
}
