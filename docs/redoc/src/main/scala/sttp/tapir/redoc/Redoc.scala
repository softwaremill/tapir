package sttp.tapir.redoc

import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

object Redoc {

  val defaultRedocVersion = "2.0.0-rc.56"

  def redocHtml(title: String, specUrl: String, redocVersion: String = defaultRedocVersion): String = s"""
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
    |<redoc spec-url='$specUrl' expand-responses="200,201"></redoc>
    |<script src="https://cdn.jsdelivr.net/npm/redoc@$redocVersion/bundles/redoc.standalone.js"></script>
    |</body>
    |</html>
    |""".stripMargin

  def apply[F[_]](
      title: String,
      spec: String,
      options: RedocUIOptions
  ): List[ServerEndpoint[Any, F]] = {
    val specName = options.specName
    val htmlName = options.htmlName

    val prefixInput: EndpointInput[Unit] = options.pathPrefix.map(stringToPath) match {
      case Nil => stringToPath("")
      case x   => x.reduce[EndpointInput[Unit]](_.and(_))
    }
    val prefixFromRoot = (options.contextPath ++ options.pathPrefix) match {
      case Nil => None
      case x   => Option(x.mkString("/"))
    }

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    def contentEndpoint(fileName: String, mediaType: MediaType) =
      baseEndpoint.in(fileName).out(stringBody).out(header(Header.contentType(mediaType)))

    val specNameLowerCase = specName.toLowerCase
    val specMediaType =
      if (specNameLowerCase.endsWith(".json")) MediaType("application", "json")
      else if (specNameLowerCase.endsWith(".yaml") || specNameLowerCase.endsWith(".yml")) MediaType("text", "yaml")
      else MediaType("text", "plain")

    val redirectToHtmlEndpoint = baseEndpoint.out(redirectOutput).serverLogicPure[F](_ => Right(s"/${concat(prefixFromRoot, htmlName)}"))
    val redirectToSlashEndpoint = baseEndpoint.in(noTrailingSlash).in(queryParams).out(redirectOutput).serverLogicPure[F] { params =>
      val queryString = if (params.toSeq.nonEmpty) s"?${params.toString}" else ""
      Right(s"/$prefixFromRoot/$queryString")
    }
    val specEndpoint = contentEndpoint(specName, specMediaType).serverLogicPure[F](_ => Right(spec))

    val html: String = redocHtml(title, s"/${concat(prefixFromRoot, specName)}", options.redocVersion)
    val htmlEndpoint = contentEndpoint(htmlName, MediaType.TextHtml).serverLogicPure[F](_ => Right(html))

    List(redirectToHtmlEndpoint, redirectToSlashEndpoint, specEndpoint, htmlEndpoint)
  }

  private def concat(prefixFromRoot: Option[String], fileName: String) = {
    prefixFromRoot.map(pref => s"$pref/$fileName").getOrElse(s"$fileName")
  }
}
