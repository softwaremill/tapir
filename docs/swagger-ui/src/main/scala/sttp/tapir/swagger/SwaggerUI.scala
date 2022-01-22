package sttp.tapir.swagger

import sttp.model.{HeaderNames, QueryParams, StatusCode}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

import java.util.Properties
import scala.io.Source

object SwaggerUI {
  private val swaggerVersion = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  private val indexHtml = {
    val s = getClass.getResourceAsStream(s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion/index.html")
    val r = Source.fromInputStream(s, "UTF-8").mkString
    s.close()
    r
  }

  /** Usage: pass `SwaggerUI[F](yaml)` endpoints to your server interpreter. Docs will be available using the `/docs` path.
    *
    * @param yaml
    *   The yaml with the OpenAPI documentation.
    * @param pathPrefix
    *   The path prefix which will be added to the documentation endpoints, as a list of path segments. Defaults to `List("docs")`, so the
    *   address of the docs will be `/docs` (unless `contextPath` is non-empty).
    * @param yamlName
    *   The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
    * @param contextPath
    *   The context path in which the documentation routes are going to be attached. Unless the endpoints are attached to `/`, this needs to
    *   be specified for redirects and yaml reference to work correctly. E.g. when context path is `List("api", "v1")`, and other parameters
    *   are left with default values, the generated full path to the yaml will be `/api/v1/docs/docs.yaml`. Defaults to `Nil`.
    */
  def apply[F[_]](
      yaml: String,
      pathPrefix: List[String] = List("docs"),
      yamlName: String = "docs.yaml",
      contextPath: List[String] = Nil
  ): List[ServerEndpoint[Any, F]] = {
    val prefixInput: EndpointInput[Unit] = pathPrefix.map(stringToPath).reduce[EndpointInput[Unit]](_.and(_))
    val prefixFromRoot = (contextPath ++ pathPrefix).mkString("/")

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    val yamlEndpoint = baseEndpoint
      .in(yamlName)
      .out(stringBody)
      .serverLogicPure[F](_ => Right(yaml))

    val oauth2Endpoint = baseEndpoint
      .in("oauth2-redirect.html")
      .in(queryParams)
      .out(redirectOutput)
      .serverLogicPure[F] { (params: QueryParams) =>
        val queryString = if (params.toSeq.nonEmpty) s"?${params.toString}" else ""
        Right(s"/$prefixFromRoot/oauth2-redirect.html$queryString")
      }

    // swagger-ui webjar comes with the petstore pre-configured; this cannot be changed at runtime
    // (see https://github.com/softwaremill/tapir/issues/1695), hence replacing the address in the served document
    val indexHtmlWithReplacedUrl = indexHtml.replace("https://petstore.swagger.io/v2/swagger.json", s"/$prefixFromRoot/$yamlName")
    val redirectToSlashEndpoint = baseEndpoint.in(noTrailingSlash).in(queryParams).out(redirectOutput).serverLogicPure[F] { params =>
      val queryString = if (params.toSeq.nonEmpty) s"?${params.toString}" else ""
      Right(s"/$prefixFromRoot/$queryString")
    }
    val slashEndpoint = baseEndpoint.in("").out(htmlBodyUtf8).serverLogicPure[F](_ => Right(indexHtmlWithReplacedUrl))
    val indexEndpoint = baseEndpoint.in("index.html").out(htmlBodyUtf8).serverLogicPure[F](_ => Right(indexHtmlWithReplacedUrl))

    val resourcesEndpoint = resourcesGetServerEndpoint[F](prefixInput)(
      SwaggerUI.getClass.getClassLoader,
      s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/"
    )

    List(yamlEndpoint, oauth2Endpoint, redirectToSlashEndpoint, slashEndpoint, indexEndpoint, resourcesEndpoint)
  }
}
