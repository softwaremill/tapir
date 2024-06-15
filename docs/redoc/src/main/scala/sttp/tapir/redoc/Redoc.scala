package sttp.tapir.redoc

import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

object Redoc {

  val defaultRedocVersion = "2.0.0-rc.56"

  def redocHtml(
      title: String,
      specUrl: String,
      redocVersion: String = defaultRedocVersion,
      redocOptions: Option[String] = None,
      redocThemeOptionsJson: Option[String] = None
  ): String = {
    val options = redocOptions.filterNot(_.isEmpty).getOrElse("")
    val themeOptions = redocThemeOptionsJson.filterNot(_.isEmpty).fold("")(json => s"theme='$json'")
    s"""
       |<!DOCTYPE html>
       |<html>
       |<head>
       |  <title>$title</title>
       |  <!-- needed for adaptive design -->
       |  <meta charset="utf-8"/>
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <link href="https://fonts.googleapis.com/css?family=Montserrat:300,400,700|Roboto:300,400,700" rel="stylesheet">
       |
       |  <!--
       |  ReDoc doesn't change outer page styles
       |  -->
       |  <style>
       |    body {
       |      margin: 0;
       |      padding: 0;
       |    }
       |  </style>
       |</head>
       |<body>
       |  <redoc spec-url='$specUrl' expand-responses="200,201" $options $themeOptions></redoc>
       |  <script src="https://cdn.jsdelivr.net/npm/redoc@$redocVersion/bundles/redoc.standalone.js"></script>
       |</body>
       |</html>
       |""".stripMargin
  }

  def apply[F[_]](
      title: String,
      spec: String,
      options: RedocUIOptions
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
      if (specNameLowerCase.endsWith(".json")) MediaType("application", "json")
      else if (specNameLowerCase.endsWith(".yaml") || specNameLowerCase.endsWith(".yml")) MediaType("text", "yaml")
      else MediaType("text", "plain")

    val specEndpoint = contentEndpoint(specName, specMediaType).serverLogicSuccessPure[F](_ => spec)

    val specPrefix = if (options.useRelativePaths) "." else "/" + (options.contextPath ++ options.pathPrefix).mkString("/")
    val html: String = redocHtml(
      title,
      s"$specPrefix/$specName",
      options.redocVersion,
      options.redocOptions,
      options.redocThemeOptionsJson
    )
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
