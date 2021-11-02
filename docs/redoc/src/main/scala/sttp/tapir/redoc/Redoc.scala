package sttp.tapir.redoc

import sttp.model.{Header, HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

object Redoc {
  def apply[F[_]](
      title: String,
      yaml: String,
      prefix: List[String] = List("docs"),
      yamlName: String = "docs.yaml",
      htmlName: String = "index.html",
      redocVersion: String = "2.0.0-rc.56"
  ): List[ServerEndpoint[Any, F]] = {
    val html: String =
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
         |<redoc spec-url='$yamlName' expand-responses="200,201"></redoc>
         |<script src="https://cdn.jsdelivr.net/npm/redoc@$redocVersion/bundles/redoc.standalone.js"></script>
         |</body>
         |</html>
         |""".stripMargin

    val prefixInput = prefix.map(stringToPath).reduce[EndpointInput[Unit]](_.and(_))
    val prefixAsPath = prefix.mkString("/")

    val baseEndpoint = infallibleEndpoint.get.in(prefixInput)
    def contentEndpoint(fileName: String, mediaType: MediaType) =
      baseEndpoint.in(fileName).out(stringBody).out(header(Header.contentType(mediaType)))
    val redirectOutput = statusCode(StatusCode.PermanentRedirect).and(header[String](HeaderNames.Location))

    val redirectToHtmlEndpoint = baseEndpoint.out(redirectOutput).serverLogicPure[F](_ => Right(s"/$prefixAsPath/$htmlName"))
    val yamlEndpoint = contentEndpoint(yamlName, MediaType("text", "yaml")).serverLogicPure[F](_ => Right(yaml))
    val htmlEndpoint = contentEndpoint(htmlName, MediaType.TextHtml).serverLogicPure[F](_ => Right(html))

    List(redirectToHtmlEndpoint, yamlEndpoint, htmlEndpoint)
  }
}
