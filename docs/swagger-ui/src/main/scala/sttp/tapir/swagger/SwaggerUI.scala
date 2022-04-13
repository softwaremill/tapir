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

  private val swaggerInitializerJs = {
    val s = getClass.getResourceAsStream(s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion/swagger-initializer.js")
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
    val prefixInput: EndpointInput[Unit] = options.pathPrefix.map(stringToPath).foldLeft(emptyInput)(_.and(_))
    val prefixFromRoot = (options.contextPath ++ options.pathPrefix) match {
      case Nil => None
      case x   => Option(x.mkString("/"))
    }

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    val yamlEndpoint = baseEndpoint
      .in(options.yamlName)
      .out(stringBody)
      .serverLogicPure[F](_ => Right(yaml))

    val oauth2redirectFileName = "oauth2-redirect.html"
    val oauth2Endpoint = baseEndpoint
      .in(oauth2redirectFileName)
      .in(queryParams)
      .out(redirectOutput)
      .serverLogicPure[F] { (params: QueryParams) =>
        val queryString = if (params.toSeq.nonEmpty) s"?${params.toString}" else ""
        Right(s"/${concat(prefixFromRoot, oauth2redirectFileName)}$queryString")
      }

    // swagger-ui webjar comes with the petstore pre-configured; this cannot be changed at runtime
    // (see https://github.com/softwaremill/tapir/issues/1695), hence replacing the address in the served document
    val swaggerInitializerJsWithReplacedUrl =
      swaggerInitializerJs.replace("https://petstore.swagger.io/v2/swagger.json", s"/${concat(prefixFromRoot, options.yamlName)}")

    val redirectToSlashEndpoint = baseEndpoint.in(noTrailingSlash).in(queryParams).out(redirectOutput).serverLogicPure[F] { params =>
      val queryString = if (params.toSeq.nonEmpty) s"?${params.toString}" else ""
      Right(s"/${concat(prefixFromRoot, queryString)}")
    }

    val textJavascriptUtf8: EndpointIO.Body[String, String] = stringBodyUtf8AnyFormat(Codec.string.format(CodecFormat.TextJavascript()))
    val swaggerInitializerJsEndpoint =
      baseEndpoint.in("swagger-initializer.js").out(textJavascriptUtf8).serverLogicPure[F](_ => Right(swaggerInitializerJsWithReplacedUrl))

    val resourcesEndpoint = resourcesGetServerEndpoint[F](prefixInput)(
      SwaggerUI.getClass.getClassLoader,
      s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/"
    )

    if (emptyInput.equals(prefixInput))
      List(yamlEndpoint, oauth2Endpoint, swaggerInitializerJsEndpoint, resourcesEndpoint)
    else
      List(yamlEndpoint, redirectToSlashEndpoint, oauth2Endpoint, swaggerInitializerJsEndpoint, resourcesEndpoint)

  }

  private def concat(prefixFromRoot: Option[String], fileName: String) = {
    prefixFromRoot.map(pref => s"$pref/$fileName").getOrElse(s"$fileName")
  }
}
