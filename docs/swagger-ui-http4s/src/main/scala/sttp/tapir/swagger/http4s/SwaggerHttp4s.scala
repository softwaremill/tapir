package sttp.tapir.swagger.http4s

import java.util.Properties

import cats.effect.{Blocker, ContextShift, Sync}
import org.http4s.{HttpRoutes, StaticFile, Uri}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

import scala.concurrent.ExecutionContext

/**
  * Usage: add `new SwaggerHttp4s(yaml).routes[F]` to your http4s router. For example:
  * `Router("/docs" -> new SwaggerHttp4s(yaml).routes[IO])`.
  *
  * When using a custom `contextPath` is used, replace `/docs` with that value.
  *
  * @param yaml        The yaml with the OpenAPI documentation.
  * @param contextPath The context in which the documentation will be served. Defaults to `docs`, so the address
  *                    of the docs will be `/docs`.
  * @param yamlName    The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  */
class SwaggerHttp4s(yaml: String, contextPath: String = "docs", yamlName: String = "docs.yaml") {
  private val swaggerVersion = {
    val p = new Properties()
    val pomProperties = getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  def routes[F[_]: ContextShift: Sync]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root =>
        PermanentRedirect(Location(Uri.fromString(s"/$contextPath/index.html?url=/$contextPath/$yamlName").right.get))
      case GET -> Root / `yamlName` =>
        Ok(yaml)
      case r =>
        StaticFile
          .fromResource(
            s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion${r.pathInfo}",
            Blocker.liftExecutionContext(ExecutionContext.global)
          )
          .getOrElseF(NotFound())
    }
  }
}
