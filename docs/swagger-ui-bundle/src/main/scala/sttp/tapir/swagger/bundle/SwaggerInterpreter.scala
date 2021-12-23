package sttp.tapir.swagger.bundle

import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUI

trait SwaggerInterpreter {
  def docsExtensions: List[DocsExtension[_]]
  def docsOptions: OpenAPIDocsOptions
  def prefix: List[String]
  def yamlName: String
  def contextPath: List[String]
  def addServerWhenContextPathPresent: Boolean
  def customiseDocsModel: OpenAPI => OpenAPI

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      info: Info
  ): List[ServerEndpoint[Any, F]] = {
    val openapi0 = OpenAPIDocsInterpreter(docsOptions).toOpenAPI(endpoints, info)
    val openapi1 = if (addServerWhenContextPathPresent && contextPath.nonEmpty) {
      openapi0.addServer(s"/${contextPath.mkString("/")}")
    } else openapi0
    val openapi = customiseDocsModel(openapi1)
    val yaml = openapi.toYaml
    SwaggerUI(yaml, prefix, yamlName, contextPath)
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
    * @param pathPrefix
    *   The path prefix which will be added to the documentation endpoints, as a list of path segments. Defaults to `List("docs")`, so the
    *   address of the docs will be `/docs` (unless `contextPath` is non-empty).
    * @param yamlName
    *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
    * @param contextPath
    *   The context path in which the documentation routes are going to be attached. Unless the endpoints are attached to `/`, this needs to
    *   be specified for redirects and yaml reference to work correctly. E.g. when context path is `List("api", "v1")`, and other parameters
    *   are left with default values, the generated full path to the yaml will be `/api/v1/docs/docs.yaml`. Defaults to `Nil`.
    * @param addServerWhenContextPathPresent
    *   Should a default server entry be added to the generated [[OpenAPI]] model pointing to the context path, if a non-empty context path
    *   is specified. In presence of a context path, either the endpoints need to be prefixed with the context path, or a server entry must
    *   be added, for invocations from within the Swagger UI to work properly. Defaults to `true`.
    * @param customiseDocsModel
    *   A function which can customise the [[OpenAPI]] model generated for the given endpoints, allowing adding e.g. server information.
    */
  def apply(
      docsExtensions: List[DocsExtension[_]] = Nil,
      docsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default,
      pathPrefix: List[String] = List("docs"),
      yamlName: String = "docs.yaml",
      contextPath: List[String] = Nil,
      addServerWhenContextPathPresent: Boolean = true,
      customiseDocsModel: OpenAPI => OpenAPI = identity
  ): SwaggerInterpreter = {
    val exts = docsExtensions
    val opts = docsOptions
    val p = pathPrefix
    val yn = yamlName
    val cp = contextPath
    val cdm = customiseDocsModel
    val aswcpp = addServerWhenContextPathPresent
    new SwaggerInterpreter {
      override val docsExtensions: List[DocsExtension[_]] = exts
      override val docsOptions: OpenAPIDocsOptions = opts
      override val prefix: List[String] = p
      override val yamlName: String = yn
      override val contextPath: List[String] = cp
      override val addServerWhenContextPathPresent: Boolean = aswcpp
      override val customiseDocsModel: OpenAPI => OpenAPI = cdm
    }
  }
}
