package sttp.tapir.swagger.akkahttp

import java.util.Properties

import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path

/** Usage: add `new SwaggerAkka(yaml).routes` to your akka-http routes. Docs will be available using the `/docs` path.
  *
  * @param yaml        The yaml with the OpenAPI documentation.
  * @param contextPath The context in which the documentation will be served. Defaults to `docs`, so the address
  *                    of the docs will be `/docs`. Should not start, nor end with a '/'.
  * @param yamlName    The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  */
class SwaggerAkka(yaml: String, contextPath: String = "docs", yamlName: String = "docs.yaml") {
  require(!contextPath.startsWith("/") && !contextPath.endsWith("/"))

  private val redirectToIndex: Route =
    redirect(s"/$contextPath/index.html?url=/$contextPath/$yamlName", StatusCodes.PermanentRedirect)

  // needed only if you use oauth2 authorization
  private def redirectToOath2(query: String): Route =
    redirect(s"/$contextPath/oauth2-redirect.html$query", StatusCodes.PermanentRedirect)

  private val swaggerVersion = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  private val contextPathMatcher = {
    def toPathMatcher(segment: String) = PathMatcher(segment :: Path.Empty, ())
    contextPath.split('/').map(toPathMatcher).reduceLeft(_ / _)
  }

  val routes: Route =
    concat(
      pathPrefix(contextPathMatcher) {
        concat(
          pathEndOrSingleSlash { redirectToIndex },
          path(yamlName) { complete(yaml) },
          getFromResourceDirectory(s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/")
        )
      },
      // needed only if you use oauth2 authorization
      path("oauth2-redirect.html") { request =>
        redirectToOath2(request.request.uri.rawQueryString.map(s => "?" + s).getOrElse(""))(request)
      }
    )
}
