package sttp.tapir.scalar

import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

object Scalar {

  def scalarHtml(title: String, specUrl: String, options: ScalarUIOptions): String = {
    val scriptUrl = options.scalarVersion match {
      case Some(version) => s"https://cdn.jsdelivr.net/npm/@scalar/api-reference@$version/dist/browser/standalone.js"
      case None          => "https://cdn.jsdelivr.net/npm/@scalar/api-reference"
    }
    val configJs = renderConfig(specUrl, options.scalarConfiguration)
    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <title>$title</title>
       |  <meta charset="utf-8"/>
       |  <meta name="viewport" content="width=device-width, initial-scale=1"/>
       |</head>
       |<body>
       |  <div id="app"></div>
       |  <script src="$scriptUrl"></script>
       |  <script>
       |    Scalar.createApiReference('#app', {
       |      $configJs
       |    })
       |  </script>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def jsStr(s: String): String =
    "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "") + "'"

  private def renderConfig(specUrl: String, cfg: ScalarConfiguration): String = {
    val fields: List[String] = List(
      Some(s"url: ${jsStr(specUrl)}"),
      cfg.authentication.map(v => s"authentication: $v"),
      cfg.baseServerURL.map(v => s"baseServerURL: ${jsStr(v)}"),
      cfg.customCss.map(v => s"customCss: ${jsStr(v)}"),
      cfg.darkMode.map(v => s"darkMode: $v"),
      cfg.defaultHttpClient.map(v => s"defaultHttpClient: $v"),
      cfg.defaultOpenAllTags.map(v => s"defaultOpenAllTags: $v"),
      cfg.defaultOpenFirstTag.map(v => s"defaultOpenFirstTag: $v"),
      cfg.documentDownloadType.map(v => s"documentDownloadType: ${jsStr(v)}"),
      cfg.expandAllModelSections.map(v => s"expandAllModelSections: $v"),
      cfg.expandAllResponses.map(v => s"expandAllResponses: $v"),
      cfg.favicon.map(v => s"favicon: ${jsStr(v)}"),
      cfg.forceDarkModeState.map(v => s"forceDarkModeState: ${jsStr(v)}"),
      cfg.hideClientButton.map(v => s"hideClientButton: $v"),
      cfg.hideDarkModeToggle.map(v => s"hideDarkModeToggle: $v"),
      cfg.hiddenClients.map(v => s"hiddenClients: $v"),
      cfg.hideModels.map(v => s"hideModels: $v"),
      cfg.hideSearch.map(v => s"hideSearch: $v"),
      cfg.hideTestRequestButton.map(v => s"hideTestRequestButton: $v"),
      cfg.isLoading.map(v => s"isLoading: $v"),
      cfg.layout.map(v => s"layout: ${jsStr(v)}"),
      cfg.metaData.map(v => s"metaData: $v"),
      cfg.mcp.map(v => s"mcp: $v"),
      cfg.oauth2RedirectUri.map(v => s"oauth2RedirectUri: ${jsStr(v)}"),
      cfg.operationTitleSource.map(v => s"operationTitleSource: ${jsStr(v)}"),
      cfg.orderRequiredPropertiesFirst.map(v => s"orderRequiredPropertiesFirst: $v"),
      cfg.orderSchemaPropertiesBy.map(v => s"orderSchemaPropertiesBy: ${jsStr(v)}"),
      cfg.pathRouting.map(v => s"pathRouting: $v"),
      cfg.persistAuth.map(v => s"persistAuth: $v"),
      cfg.proxyUrl.map(v => s"proxyUrl: ${jsStr(v)}"),
      cfg.searchHotKey.map(v => s"searchHotKey: ${jsStr(v)}"),
      cfg.servers.map(v => s"servers: $v"),
      cfg.showDeveloperTools.map(v => s"showDeveloperTools: ${jsStr(v)}"),
      cfg.showOperationId.map(v => s"showOperationId: $v"),
      cfg.showSidebar.map(v => s"showSidebar: $v"),
      cfg.telemetry.map(v => s"telemetry: $v"),
      cfg.theme.map(v => s"theme: ${jsStr(v)}"),
      cfg.withDefaultFonts.map(v => s"withDefaultFonts: $v")
    ).flatten
    fields.mkString(",\n      ")
  }

  def apply[F[_]](
      title: String,
      spec: String,
      options: ScalarUIOptions
  ): List[ServerEndpoint[Any, F]] = {
    val specName = options.specName
    val htmlName = options.htmlName

    val prefixInput: EndpointInput[Unit] = options.pathPrefix.map(stringToPath).foldLeft(emptyInput)(_.and(_))

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    def contentEndpoint(fileName: String, mediaType: MediaType) =
      baseEndpoint.in(fileName).out(stringBody).out(header(Header.contentType(mediaType)))

    val specNameLowerCase = specName.toLowerCase
    val specMediaType =
      if (specNameLowerCase.endsWith(".json")) MediaType.ApplicationJson
      else if (specNameLowerCase.endsWith(".yaml") || specNameLowerCase.endsWith(".yml")) MediaType("text", "yaml")
      else MediaType.TextPlain

    val specEndpoint = contentEndpoint(specName, specMediaType).serverLogicSuccessPure[F](_ => spec)

    val specPrefix = if (options.useRelativePaths) "." else "/" + (options.contextPath ++ options.pathPrefix).mkString("/")
    val html: String = scalarHtml(title, s"$specPrefix/$specName", options)
    val htmlEndpoint = contentEndpoint(htmlName, MediaType.TextHtml).serverLogicSuccessPure[F](_ => html)

    val lastSegmentInput: EndpointInput[Option[String]] = extractFromRequest(_.uri.path.lastOption)

    val redirectToHtmlEndpoint =
      baseEndpoint
        .in(lastSegmentInput)
        .out(redirectOutput)
        .serverLogicSuccessPure[F] { lastSegment =>
          if (options.useRelativePaths) {
            val pathFromLastSegment: String = lastSegment match {
              case Some(s) if s.nonEmpty => s + "/"
              case _                     => ""
            }
            s"./$pathFromLastSegment$htmlName"
          } else
            options.contextPath ++ options.pathPrefix match {
              case Nil      => s"/$htmlName"
              case segments => s"/${segments.mkString("/")}/$htmlName"
            }
        }

    List(specEndpoint, htmlEndpoint, redirectToHtmlEndpoint)
  }
}
