package sttp.tapir.swagger.bundle

import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.{SwaggerUI, SwaggerUIOptions}

trait SwaggerInterpreter {
  def openAPIInterpreterOptions: OpenAPIDocsOptions
  def customiseDocsModel: OpenAPI => OpenAPI
  def swaggerUIOptions: SwaggerUIOptions
  def addServerWhenContextPathPresent: Boolean

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): List[ServerEndpoint[Any, F]] = {
    val openapi0 = OpenAPIDocsInterpreter(openAPIInterpreterOptions).toOpenAPI(endpoints, info, docsExtensions)
    val openapi1 = if (addServerWhenContextPathPresent && swaggerUIOptions.contextPath.nonEmpty) {
      openapi0.addServer(s"/${swaggerUIOptions.contextPath.mkString("/")}")
    } else openapi0
    val openapi = customiseDocsModel(openapi1)
    val yaml = openapi.toYaml
    SwaggerUI(yaml, swaggerUIOptions)
  }

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      info: Info
  ): List[ServerEndpoint[Any, F]] = fromEndpoints(endpoints, info, Nil)

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      title: String,
      version: String
  ): List[ServerEndpoint[Any, F]] = fromEndpoints(endpoints, Info(title, version), Nil)

  def fromServerEndpoints[F[_]](
      endpoints: List[ServerEndpoint[_, F]],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): List[ServerEndpoint[Any, F]] = fromEndpoints(endpoints.map(_.endpoint), info, docsExtensions)

  def fromServerEndpoints[F[_]](
      endpoints: List[ServerEndpoint[_, F]],
      info: Info
  ): List[ServerEndpoint[Any, F]] = fromEndpoints(endpoints.map(_.endpoint), info, Nil)

  def fromServerEndpoints[F[_]](
      endpoints: List[ServerEndpoint[_, F]],
      title: String,
      version: String
  ): List[ServerEndpoint[Any, F]] =
    fromEndpoints(endpoints.map(_.endpoint), Info(title, version), Nil)
}

object SwaggerInterpreter {

  /** Allows interpreting lists of [[sttp.tapir.Endpoint]]s or [[ServerEndpoint]]s as Swagger UI docs. The docs will be serialised in the
    * yaml format, and will be available using the configured `prefix` path, by default `/docs`.
    *
    * Usage: pass the result of `SwaggerInterpreter().fromEndpoints[F]` to your server interpreter.
    *
    * @param openAPIInterpreterOptions
    *   The options passed to the [[OpenAPIDocsInterpreter]], customising the process of interpreting endpoints as OpenAPI documentation.
    * @param customiseDocsModel
    *   A function which can customise the [[OpenAPI]] model created by the [[OpenAPIDocsInterpreter]] for the given endpoints, allowing
    *   adding e.g. server information.
    * @param swaggerUIOptions
    *   Options passed to [[SwaggerUI]] to customise how the documentation is exposed, e.g. the path.
    * @param addServerWhenContextPathPresent
    *   Should a default server entry be added to the generated [[OpenAPI]] model pointing to the context path, if a non-empty context path
    *   is specified in `swaggerUIOptions`. In presence of a context path, either the endpoints need to be prefixed with the context path,
    *   or a server entry must be added, for invocations from within the Swagger UI to work properly. Defaults to `true`.
    */
  def apply(
      openAPIInterpreterOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default,
      customiseDocsModel: OpenAPI => OpenAPI = identity,
      swaggerUIOptions: SwaggerUIOptions = SwaggerUIOptions.default,
      addServerWhenContextPathPresent: Boolean = true
  ): SwaggerInterpreter = {
    val opts = openAPIInterpreterOptions
    val cdm = customiseDocsModel
    val s = swaggerUIOptions
    val aswcpp = addServerWhenContextPathPresent
    new SwaggerInterpreter {
      override val openAPIInterpreterOptions: OpenAPIDocsOptions = opts
      override val customiseDocsModel: OpenAPI => OpenAPI = cdm
      override val swaggerUIOptions: SwaggerUIOptions = s
      override val addServerWhenContextPathPresent: Boolean = aswcpp
    }
  }
}
