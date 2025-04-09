package sttp.tapir.swagger

import sttp.model.{HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.files._
import sttp.tapir.server.ServerEndpoint

import java.util.Properties
import scala.io.Source

object SwaggerUI {
  private val swaggerVersion =
    Option(getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties"))
      .map { pomProperties =>
        val p = new Properties()
        try p.load(pomProperties)
        finally pomProperties.close()
        p.getProperty("version")
      }
      .getOrElse(
        throw new ExceptionInInitializerError(
          "META-INF resources are missing, please check " +
            "https://tapir.softwaremill.com/en/latest/docs/openapi.html#using-swaggerui-with-sbt-assembly"
        )
      )

  private val swaggerInitializerJs = {
    val s = getClass.getResourceAsStream(s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion/swagger-initializer.js")
    val r = Source.fromInputStream(s, "UTF-8").mkString
    s.close()
    r
  }
  
  private def optionsInjection(swaggerInitializerJs: String, options: SwaggerUIOptions) : String = {
    val swaggerInitializerJsWithExtensions = swaggerInitializerJs.replace(
      "window.ui = SwaggerUIBundle({",
      s"""window.ui = SwaggerUIBundle({
         |    showExtensions: ${options.showExtensions},""".stripMargin
    )

    val swaggerInitializerJsWithOptions = options.initializerOptions
      .map(_.foldRight(swaggerInitializerJsWithExtensions)({ (option, accumulator) =>
        accumulator.replace(
          "window.ui = SwaggerUIBundle({",
          s"""window.ui = SwaggerUIBundle({
             |    ${option._1}: ${option._2},""".stripMargin
        )
      }))
      .getOrElse(swaggerInitializerJsWithExtensions)

    val swaggerInitializerJsWithOAuthInit = options.oAuthInitOptions
      .map(
        _.foldRight(
          // injecting initOAuth call
          swaggerInitializerJsWithOptions.replace(
            "});",
            s"""});
               |window.ui.initOAuth({
               |});
               |""".stripMargin
          )
        )({ (option, accumulator) =>
          accumulator.replace(
            // injecting options for initOAuth call
            "window.ui.initOAuth({",
            s"""window.ui.initOAuth({
               |${option._1}: ${option._2},""".stripMargin
          )
        })
      )
      .getOrElse(swaggerInitializerJsWithOptions)
    swaggerInitializerJsWithOAuthInit
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
    val fullPathPrefix =
      if (options.useRelativePaths) "."
      else "/" + (options.contextPath ++ options.pathPrefix).mkString("/")

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    val yamlEndpoint = baseEndpoint
      .in(options.yamlName)
      .out(stringBodyUtf8AnyFormat(Codec.string.format(new CodecFormat {
        // #2396: although application/yaml is not official, that's what swagger ui sends in the accept header
        override def mediaType: MediaType = MediaType("application", "yaml")
      })))
      .serverLogicSuccessPure[F](_ => yaml)

    // swagger-ui webjar comes with the petstore pre-configured; this cannot be changed at runtime
    // (see https://github.com/softwaremill/tapir/issues/1695), hence replacing the address in the served document
    val swaggerInitializerJsWithReplacedUrl =
      swaggerInitializerJs.replace("https://petstore.swagger.io/v2/swagger.json", s"${concat(fullPathPrefix, options.yamlName)}")

   val swaggerInitializerJsWithOptions = optionsInjection(swaggerInitializerJsWithReplacedUrl, options)

    val textJavascriptUtf8: EndpointIO.Body[String, String] = stringBodyUtf8AnyFormat(Codec.string.format(CodecFormat.TextJavascript()))
    val swaggerInitializerJsEndpoint =
      baseEndpoint.in("swagger-initializer.js").out(textJavascriptUtf8).serverLogicSuccessPure[F](_ => swaggerInitializerJsWithOptions)

    val resourcesEndpoint = staticResourcesGetServerEndpoint[F](prefixInput)(
      SwaggerUI.getClass.getClassLoader,
      s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/"
    )

    if (options.pathPrefix == Nil) List(yamlEndpoint, swaggerInitializerJsEndpoint, resourcesEndpoint)
    else {
      val lastSegmentInput: EndpointInput[Option[String]] = extractFromRequest(request => request.pathSegments.lastOption)
      val redirectToSlashEndpoint = baseEndpoint
        .in(noTrailingSlash)
        .in(queryParams)
        .in(lastSegmentInput)
        .out(redirectOutput)
        .serverLogicSuccessPure[F] { case (params, lastSegment) =>
          val queryString = if (params.toSeq.nonEmpty) s"?${params.toString}" else ""
          val path = if (options.useRelativePaths) lastSegment.map(str => s"$str/").getOrElse("") else ""
          s"${concat(fullPathPrefix, path + queryString)}"
        }

      List(yamlEndpoint, redirectToSlashEndpoint, swaggerInitializerJsEndpoint, resourcesEndpoint)
    }

  }

  private def concat(l: String, r: String) = s"$l/$r"
}
