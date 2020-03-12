package sttp.tapir.redoc.akkahttp

import akka.http.scaladsl.model.{ContentTypes, HttpCharsets, HttpEntity, MediaType, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class RedocAkkaHttp(title: String, yaml: String, yamlName: String = "docs.yaml", redocVersion: String = "2.0.0-rc.23") {
  lazy val html: String =
    s"""
       |<!DOCTYPE html>
       |<html>
       |<head>
       |  <title>${title}</title>
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
       |<redoc spec-url='${yamlName}' expand-responses="200,201"></redoc>
       |<script src="https://cdn.jsdelivr.net/npm/redoc@${redocVersion}/bundles/redoc.standalone.js"></script>
       |</body>
       |</html>
       |""".stripMargin

  def routes: Route = {
    get {
      pathEnd {
        redirectToTrailingSlashIfMissing(StatusCodes.MovedPermanently) {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
        }
      } ~ pathSingleSlash {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
      } ~ path(yamlName) {
        complete(HttpEntity(MediaType.textWithFixedCharset("yaml", HttpCharsets.`UTF-8`), yaml))
      }
    }
  }

}
