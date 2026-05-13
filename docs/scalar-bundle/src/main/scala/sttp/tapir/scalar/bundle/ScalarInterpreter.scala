package sttp.tapir.scalar.bundle

import sttp.apispec.openapi.{Info, OpenAPI}
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.scalar.{Scalar, ScalarUIOptions}
import sttp.tapir.server.ServerEndpoint

trait ScalarInterpreter {
  def openAPIInterpreterOptions: OpenAPIDocsOptions
  def customiseDocsModel: OpenAPI => OpenAPI
  def scalarUIOptions: ScalarUIOptions
  def addServerWhenContextPathPresent: Boolean

  def fromEndpoints[F[_]](
      endpoints: List[AnyEndpoint],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): List[ServerEndpoint[Any, F]] = {
    val openapi0 = OpenAPIDocsInterpreter(openAPIInterpreterOptions).toOpenAPI(endpoints, info, docsExtensions)
    val openapi1 = if (addServerWhenContextPathPresent && scalarUIOptions.contextPath.nonEmpty) {
      openapi0.addServer(s"/${scalarUIOptions.contextPath.mkString("/")}")
    } else openapi0
    val openapi = customiseDocsModel(openapi1)
    val yaml = openapi.toYaml
    Scalar(info.title, yaml, scalarUIOptions)
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

object ScalarInterpreter {

  /** Allows interpreting lists of [[sttp.tapir.Endpoint]]s or [[ServerEndpoint]]s as Scalar docs. The docs will be serialised in the yaml
    * format, and will be available using the configured `prefix` path, by default `/docs`.
    *
    * Usage: pass the result of `ScalarInterpreter().fromEndpoints[F]` to your server interpreter.
    *
    * @param openAPIInterpreterOptions
    *   The options that will be passed to the [[OpenAPIDocsInterpreter]].
    * @param customiseDocsModel
    *   A function which can customise the [[OpenAPI]] model created by the [[OpenAPIDocsInterpreter]] for the given endpoints, allowing
    *   adding e.g. server information.
    * @param scalarUIOptions
    *   Options passed to [[Scalar]] to customise how the documentation is exposed, e.g. the path.
    * @param addServerWhenContextPathPresent
    *   Should a default server entry be added to the generated [[OpenAPI]] model pointing to the context path, if a non-empty context path
    *   is specified in `scalarUIOptions`. In presence of a context path, either the endpoints need to be prefixed with the context path,
    *   or a server entry must be added, for invocations from within the Scalar UI to work properly. Defaults to `true`.
    */
  def apply(
      openAPIInterpreterOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default,
      customiseDocsModel: OpenAPI => OpenAPI = identity,
      scalarUIOptions: ScalarUIOptions = ScalarUIOptions.default,
      addServerWhenContextPathPresent: Boolean = true
  ): ScalarInterpreter = {
    val opts = openAPIInterpreterOptions
    val cdm = customiseDocsModel
    val s = scalarUIOptions
    val aswcpp = addServerWhenContextPathPresent
    new ScalarInterpreter {
      override val openAPIInterpreterOptions: OpenAPIDocsOptions = opts
      override val customiseDocsModel: OpenAPI => OpenAPI = cdm
      override val scalarUIOptions: ScalarUIOptions = s
      override val addServerWhenContextPathPresent: Boolean = aswcpp
    }
  }
}
