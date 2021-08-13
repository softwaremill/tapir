package sttp.tapir.redoc.ziohttp

import zhttp.http.Status.OK
import zhttp.http._
import zio.Chunk

/** Usage: add `new RedocZioHttp(title, yaml).endpoint` to your zio-http servers endpoints.
  *
  * This will add the following endpoints for your zio-http server:
  * '/doc' - will answer with the Redoc UI
  * '/doc/docs.yaml' - will answer with the OpenAPI YAML
  *
  * @param title - The title of the HTML page
  * @param yaml - the yaml with the OpenAPI documentation
  * @param yamlName - the name of the file throough which the yaml documentation will be served.  Defaults to 'docs.yaml'
  * @param redocVersion - the semver version of Redoc to use.  Defaults to `2.0.0-rc.23`
  * @param rootPath - the base path for the documentation - defaults to '/doc'
  */
class RedocZioHttp(
    title: String,
    yaml: String,
    yamlName: String = "docs.yaml",
    redocVersion: String = "2.0.0-rc.23",
    rootPath: Path = Path("doc")
) {
  lazy val html: String =
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

  val yamlData: HttpData.CompleteData = HttpData.CompleteData(Chunk.fromArray(yaml.getBytes))
  val yamlContentType = List(Header.custom("Content-Type", "text/yaml"))
  val endpoint: Http[Any, Nothing, Request, UResponse] = Http.collect[Request] {
    case Method.GET -> Root / `rootPath` =>
      Response.text(html)
    case Method.GET -> Root / `rootPath` \ `yamlName` =>
      Response.HttpResponse(OK, headers = yamlContentType, content = yamlData)
  }
}
