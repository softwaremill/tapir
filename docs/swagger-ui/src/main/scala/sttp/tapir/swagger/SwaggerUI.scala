package sttp.tapir.swagger

import sttp.model.{HeaderNames, QueryParams, StatusCode}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

import java.util.Properties

object SwaggerUI {
  private val swaggerVersion = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  /** Usage: pass `SwaggerUI[F](yaml)` endpoints to your server interpreter. Docs will be available using the `/docs` path.
    *
    * @param yaml
    *   The yaml with the OpenAPI documentation.
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
  def apply[F[_]](
      yaml: String,
      prefix: List[String] = List("docs"),
      yamlName: String = "docs.yaml",
      basePrefix: List[String] = Nil
  ): List[ServerEndpoint[Any, F]] = {
    val prefixInput: EndpointInput[Unit] = prefix.map(stringToPath).reduce[EndpointInput[Unit]](_.and(_))
    val prefixAsPath = (basePrefix ++ prefix).mkString("/")

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    val yamlEndpoint = baseEndpoint
      .in(yamlName)
      .out(stringBody)
      .serverLogicPure[F](_ => Right(yaml))

    val redirectToIndexEndpoint = baseEndpoint
      .in(queryParams)
      .out(redirectOutput)
      .serverLogicPure[F] { (params: QueryParams) =>
        val paramsWithUrl = params.param("url", s"/$prefixAsPath/$yamlName")
        Right(s"/$prefixAsPath/index.html?${paramsWithUrl.toString}")
      }

    val oauth2Endpoint = baseEndpoint
      .in("oauth2-redirect.html")
      .in(queryParams)
      .out(redirectOutput)
      .serverLogicPure[F] { (params: QueryParams) =>
        val queryString = if (params.toSeq.nonEmpty) s"?${params.toString}" else ""
        Right(s"/$prefixAsPath/oauth2-redirect.html$queryString")
      }

    val resourcesEndpoint = resourcesGetServerEndpoint[F](prefixInput)(
      SwaggerUI.getClass.getClassLoader,
      s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/"
    )

    List(yamlEndpoint, redirectToIndexEndpoint, oauth2Endpoint, resourcesEndpoint)
  }
}
