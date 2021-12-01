package sttp.tapir.swagger.bundle

import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUI

trait SwaggerInterpreter {
  def docsExtensions: List[DocsExtension[_]]
  def docsOptions: OpenAPIDocsOptions
  def prefix: List[String]
  def yamlName: String
  def basePrefix: List[String]

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      info: Info
  ): List[ServerEndpoint[Any, F]] = {
    val yaml = OpenAPIDocsInterpreter(docsOptions).toOpenAPI(endpoints, info).toYaml
    SwaggerUI(yaml, prefix, yamlName, basePrefix)
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

object SwaggerInterpreter {

  /** Allows interpreting lists of [[sttp.tapir.Endpoint]]s or [[ServerEndpoint]]s as Swagger UI docs. The docs will be serialised in the
    * yaml format, and will be available using the configured `prefix` path, by default `/docs`.
    *
    * Usage: pass the result of `SwaggerInterpreter().fromEndpoints[F]` to your server interpreter.
    *
    * @param docsExtensions
    *   The top-level documentation extensions to be included in the generated OpenAPI docs.
    * @param prefix
    *   The path prefix for which the documentation endpoint will be created, as a list of path segments. Defaults to `List(docs)`, so the
    *   address of the docs will be `/docs` (unless <code>basePrefix</code> is specified)
    * @param yamlName
    *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
    * @param basePrefix
    *   The base path prefix where the documentation routes are going to be attached. Unless the endpoint will served from `/`, the base
    *   path prefix must be specified for redirect to work correctly. Defaults to `Nil`, so it is assumed that the endpoint base path is
    *   `/`.
    */
  def apply(
      docsExtensions: List[DocsExtension[_]] = Nil,
      docsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default,
      prefix: List[String] = List("docs"),
      yamlName: String = "docs.yaml",
      basePrefix: List[String] = Nil
  ): SwaggerInterpreter = {
    val exts = docsExtensions
    val opts = docsOptions
    val p = prefix
    val yn = yamlName
    val bp = basePrefix
    new SwaggerInterpreter {
      override val docsExtensions: List[DocsExtension[_]] = exts
      override val docsOptions: OpenAPIDocsOptions = opts
      override val prefix: List[String] = p
      override val yamlName: String = yn
      override val basePrefix: List[String] = bp
    }
  }
}
