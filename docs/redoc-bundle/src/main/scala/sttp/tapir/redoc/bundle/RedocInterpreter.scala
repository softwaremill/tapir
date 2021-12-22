package sttp.tapir.redoc.bundle

import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.redoc.Redoc
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.apispec.DocsExtension

trait RedocInterpreter {
  def docsExtensions: List[DocsExtension[_]]
  def docsOptions: OpenAPIDocsOptions
  def prefix: List[String]
  def yamlName: String
  def htmlName: String
  def redocVersion: String

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      info: Info
  ): List[ServerEndpoint[Any, F]] = {
    val yaml = OpenAPIDocsInterpreter(docsOptions).toOpenAPI(endpoints, info).toYaml
    Redoc(info.title, yaml, prefix, yamlName, htmlName, redocVersion)
  }

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      title: String,
      version: String
  ): List[ServerEndpoint[Any, F]] = fromEndpoints(endpoints, Info(title, version))

  def fromServerEndpoints[F[_]](
      endpoints: List[ServerEndpoint[_, F]],
      info: Info
  ): List[ServerEndpoint[Any, F]] =
    fromEndpoints(endpoints.map(_.endpoint), info)

  def fromServerEndpoints[F[_]](
      endpoints: List[ServerEndpoint[_, F]],
      title: String,
      version: String
  ): List[ServerEndpoint[Any, F]] =
    fromEndpoints(endpoints.map(_.endpoint), Info(title, version))
}

object RedocInterpreter {

  /** Allows interpreting lists of [[sttp.tapir.Endpoint]]s or [[ServerEndpoint]]s as Redoc docs. The docs will be serialised in the yaml
    * format, and will be available using the configured `prefix` path, by default `/docs`.
    *
    * Usage: pass the result of `SwaggerInterpreter().fromEndpoints[F]` to your server interpreter.
    *
    * @param docsExtensions
    *   The top-level documentation extensions to be included in the generated OpenAPI docs.
    * @param docsOptions
    *   The options that will be passed to the [[OpenAPIDocsInterpreter]].
    * @param prefix
    *   The path prefix from which the documentation will be served, as a list of path segments. Defaults to `List(docs)`, so the address of
    *   the docs will be `/docs`.
    * @param yamlName
    *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
    */
  def apply(
      docsExtensions: List[DocsExtension[_]] = Nil,
      docsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default,
      prefix: List[String] = List("docs"),
      yamlName: String = "docs.yaml",
      htmlName: String = "index.html",
      redocVersion: String = "2.0.0-rc.56"
  ): RedocInterpreter = {
    val exts = docsExtensions
    val opts = docsOptions
    val p = prefix
    val yn = yamlName
    val hn = htmlName
    val rv = redocVersion
    new RedocInterpreter {
      override val docsExtensions: List[DocsExtension[_]] = exts
      override val docsOptions: OpenAPIDocsOptions = opts
      override val prefix: List[String] = p
      override val yamlName: String = yn
      override val htmlName: String = hn
      override val redocVersion: String = rv
    }
  }
}
