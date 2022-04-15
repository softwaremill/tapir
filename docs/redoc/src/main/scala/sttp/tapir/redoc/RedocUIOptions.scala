package sttp.tapir.redoc

/** @param pathPrefix
  *   The path prefix which will be added to the documentation endpoints, as a list of path segments. Defaults to `List("docs")`, so the
  *   address of the docs will be `./docs` (relative to the context in which the interpreted docs endpoints are attached, and unless
  *   `contextPath` is non-empty).
  * @param specName
  *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param htmlName
  *   the name of the file, through which the html documentation will be served. Defaults to `index.html`.
  * @param contextPath
  *   The optional context path in which the interpreted documentation endpoints are going to be attached. Unless the endpoints are attached
  *   to `/`, this can be specified to create absolute (instead of relative) yaml references and redirects. E.g. when context path is
  *   `List("api", "v1")`, and other parameters are left with default values, the generated full path to the yaml will be
  *   `/api/v1/docs/docs.yaml`, instead of `./docs/docs.yaml`. Defaults to `Nil`.
  * @param redocVersion
  *   Version of Redoc library
  */
case class RedocUIOptions(pathPrefix: List[String], specName: String, htmlName: String, contextPath: List[String], redocVersion: String) {
  def pathPrefix(pathPrefix: List[String]): RedocUIOptions = copy(pathPrefix = pathPrefix)
  def specName(specName: String): RedocUIOptions = copy(specName = specName)
  def htmlName(htmlName: String): RedocUIOptions = copy(htmlName = htmlName)
  def contextPath(contextPath: List[String]): RedocUIOptions = copy(contextPath = contextPath)
  def redocVersion(redocVersion: String): RedocUIOptions = copy(redocVersion = redocVersion)
}

object RedocUIOptions {
  val default: RedocUIOptions = RedocUIOptions(List("docs"), "docs.yaml", "index.html", Nil, "2.0.0-rc.56")
}
