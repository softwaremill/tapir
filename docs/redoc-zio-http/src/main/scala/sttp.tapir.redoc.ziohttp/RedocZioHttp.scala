package sttp.tapir.redoc.ziohttp

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
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
  * @param htmlName - the name of the Redoc HTML, defaults to 'redoc.html'
  * @param yamlName - the name of the file throough which the yaml documentation will be served.  Defaults to 'docs.yaml'
  * @param redocVersion - the semver version of Redoc to use.  Defaults to `2.0.0-rc.23`
  * @param contextPath - the base path for the documentation - defaults to '/docs'
  */
class RedocZioHttp(
    title: String,
    yaml: String,
    htmlName: String = "redoc.html",
    yamlName: String = "docs.yaml",
    redocVersion: String = "2.0.0-rc.23",
    contextPath: String = "docs"
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
  val htmlData: HttpData.CompleteData = HttpData.CompleteData(Chunk.fromArray(html.getBytes()))
  val yamlContentType = List(Header.custom(HttpHeaderNames.CONTENT_TYPE.toString, "text/yaml"))
  val htmlContentType = List(Header.custom(HttpHeaderNames.CONTENT_TYPE.toString, HttpHeaderValues.TEXT_HTML))
  val endpoint: Http[Any, Nothing, Request, UResponse] = Http.collect[Request] {
    case Method.GET -> Root / `contextPath` =>
      val location = s"/$contextPath/$htmlName"
      Response.http(Status.MOVED_PERMANENTLY, List(Header.custom("Location", location)))
    case Method.GET -> Root / `contextPath` / `htmlName` =>
      Response.HttpResponse(OK, headers = htmlContentType, htmlData)
    case Method.GET -> Root / `contextPath` / `yamlName` =>
      Response.HttpResponse(OK, headers = yamlContentType, content = yamlData)
  }
}
