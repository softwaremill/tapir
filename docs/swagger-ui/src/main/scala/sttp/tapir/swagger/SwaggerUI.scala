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
    * @param options
    *   Options to customise how the documentation is exposed through SwaggerUI, e.g. the path.
    */
  def apply[F[_]](yaml: String, options: SwaggerUIOptions = SwaggerUIOptions.default): List[ServerEndpoint[Any, F]] = {
    val prefixInput: EndpointInput[Unit] = options.pathPrefix.map(stringToPath).reduce[EndpointInput[Unit]](_.and(_))
    val prefixFromRoot = (options.contextPath ++ options.pathPrefix).mkString("/")

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    val yamlEndpoint = baseEndpoint
      .in(options.yamlName)
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
    val indexHtmlWithReplacedUrl = indexHtml.replace("https://petstore.swagger.io/v2/swagger.json", s"/$prefixFromRoot/${options.yamlName}")
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
