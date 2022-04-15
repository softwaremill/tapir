package sttp.tapir.redoc.bundle

import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.redoc.{Redoc, RedocUIOptions}
import sttp.tapir.server.ServerEndpoint

trait RedocInterpreter {
  def openAPIInterpreterOptions: OpenAPIDocsOptions
  def customiseDocsModel: OpenAPI => OpenAPI
  def redocUIOptions: RedocUIOptions
  def addServerWhenContextPathPresent: Boolean

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): List[ServerEndpoint[Any, F]] = {
    val openapi0 = OpenAPIDocsInterpreter(openAPIInterpreterOptions).toOpenAPI(endpoints, info, docsExtensions)
    val openapi1 = if (addServerWhenContextPathPresent && redocUIOptions.contextPath.nonEmpty) {
      openapi0.addServer(s"/${redocUIOptions.contextPath.mkString("/")}")
    } else openapi0
    val openapi = customiseDocsModel(openapi1)
    val yaml = openapi.toYaml
    Redoc(info.title, yaml, redocUIOptions)
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

object RedocInterpreter {

  /** Allows interpreting lists of [[sttp.tapir.Endpoint]]s or [[ServerEndpoint]]s as Redoc docs. The docs will be serialised in the yaml
    * format, and will be available using the configured `prefix` path, by default `/docs`.
    *
    * Usage: pass the result of `RedocInterpreter().fromEndpoints[F]` to your server interpreter.
    *
    * @param openAPIInterpreterOptions
    *   The options that will be passed to the [[OpenAPIDocsInterpreter]].
    * @param customiseDocsModel
    *   A function which can customise the [[OpenAPI]] model created by the [[OpenAPIDocsInterpreter]] for the given endpoints, allowing
    *   adding e.g. server information.
    * @param redocUIOptions
    *   Options passed to [[Redoc]] to customise how the documentation is exposed, e.g. the path.
    * @param addServerWhenContextPathPresent
    *   Should a default server entry be added to the generated [[OpenAPI]] model pointing to the context path, if a non-empty context path
    *   is specified in `swaggerUIOptions`. In presence of a context path, either the endpoints need to be prefixed with the context path,
    *   or a server entry must be added, for invocations from within the Swagger UI to work properly. Defaults to `true`.
    */
  def apply(
      openAPIInterpreterOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default,
      customiseDocsModel: OpenAPI => OpenAPI = identity,
      redocUIOptions: RedocUIOptions = RedocUIOptions.default,
      addServerWhenContextPathPresent: Boolean = true
  ): RedocInterpreter = {
    val opts = openAPIInterpreterOptions
    val cdm = customiseDocsModel
    val r = redocUIOptions
    val aswcpp = addServerWhenContextPathPresent
    new RedocInterpreter {
      override val openAPIInterpreterOptions: OpenAPIDocsOptions = opts
      override val customiseDocsModel: OpenAPI => OpenAPI = cdm
      override val redocUIOptions: RedocUIOptions = r
      override val addServerWhenContextPathPresent: Boolean = aswcpp
    }
  }
}
