package sttp.tapir.swagger.akkahttp

import java.util.Properties

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes

/**
  * Usage: add `new SwaggerAkka(yaml).routes` to your akka-http routes. Docs will be available using the `/docs` path.
  *
  * @param yaml        The yaml with the OpenAPI documentation.
  * @param contextPath The context in which the documentation will be served. Defaults to `docs`, so the address
  *                    of the docs will be `/docs`.
  * @param yamlName    The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  */
class SwaggerAkka(yaml: String, contextPath: String = "docs", yamlName: String = "docs.yaml") {
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

  val routes: Route =
    pathPrefix(contextPath) {
      pathEndOrSingleSlash {
        redirectToIndex
      } ~ path(yamlName) {
        complete(yaml)
      } ~ getFromResourceDirectory(s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/")
    } ~
      // needed only if you use oauth2 authorization
      path("oauth2-redirect.html") { request =>
        redirectToOath2(request.request.uri.rawQueryString.map(s => "?" + s).getOrElse(""))(request)
      }
}
