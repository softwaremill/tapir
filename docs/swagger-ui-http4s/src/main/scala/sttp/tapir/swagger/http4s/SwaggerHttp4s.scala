package sttp.tapir.swagger.http4s

import java.util.Properties

import cats.effect.{Blocker, ContextShift, Sync}
import org.http4s.{HttpRoutes, StaticFile, Uri}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

import scala.concurrent.ExecutionContext

/**
  * Usage: add `new SwaggerHttp4s(yaml).routes[F]` to your http4s router. For example:
  * `Router("/" -> new SwaggerHttp4s(yaml).routes[IO])`
  * or, in combination with other routes:
  * `Router("/" -> (routes <+> new SwaggerHttp4s(openApiYml).routes[IO])).orNotFound`
  *
  * @param yaml        The yaml with the OpenAPI documentation.
  * @param contextPath The context in which the documentation will be served. Defaults to `docs`, so the address
  *                    of the docs will be `/docs`.
  * @param yamlName    The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  * @param redirectQuery Additional query parameters to add when redirecting from the context path root to
  *                      Swagger's `index.html`. For example, `Map(defaultModelsExpandDepth, Seq("0"))`. Defaults to
  *                      an empty map.
  */
class SwaggerHttp4s(
    yaml: String,
    contextPath: String = "docs",
    yamlName: String = "docs.yaml",
    redirectQuery: Map[String, Seq[String]] = Map.empty
) {
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
      case path @ GET -> Root / `contextPath` =>
        val queryParameters = Map("url" -> Seq(s"${path.uri}/$yamlName")) ++ redirectQuery
        Uri
          .fromString(s"${path.uri}/index.html")
          .map(uri => uri.setQueryParams(queryParameters))
          .map(uri => PermanentRedirect(Location(uri)))
          .getOrElse(NotFound())
      case GET -> Root / `contextPath` / `yamlName` =>
        Ok(yaml)
      case GET -> Root / `contextPath` / swaggerResource =>
        StaticFile
          .fromResource(
            s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion/$swaggerResource",
            Blocker.liftExecutionContext(ExecutionContext.global)
          )
          .getOrElseF(NotFound())
    }
  }
}
